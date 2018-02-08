// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.cluster.model.attribute;

import java.util.Collection;

import io.vlingo.actors.Logger;
import io.vlingo.cluster.model.application.ClusterApplication;
import io.vlingo.cluster.model.attribute.Confirmables.Confirmable;
import io.vlingo.cluster.model.attribute.message.AddAttribute;
import io.vlingo.cluster.model.attribute.message.ApplicationMessageType;
import io.vlingo.cluster.model.attribute.message.ConfirmAttribute;
import io.vlingo.cluster.model.attribute.message.ConfirmAttributeSet;
import io.vlingo.cluster.model.attribute.message.CreateAttributeSet;
import io.vlingo.cluster.model.attribute.message.RemoveAttribute;
import io.vlingo.cluster.model.attribute.message.ReplaceAttribute;
import io.vlingo.cluster.model.message.ApplicationSays;
import io.vlingo.cluster.model.outbound.OperationalOutboundStream;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;

public final class ConfirmingDistributor {
  private final ClusterApplication application;
  private final Confirmables confirmables;
  
  private final Collection<Node> allOtherNodes;
  private final Logger logger;
  private final Node node;
  private final OperationalOutboundStream outbound;

  ConfirmingDistributor(final ClusterApplication application, final Node node, final OperationalOutboundStream outbound, final Configuration configuration) {
    this.application = application;
    this.logger = configuration.logger();
    this.node = node;
    this.outbound = outbound;
    this.allOtherNodes = configuration.allOtherNodes(node.id());
    this.confirmables = new Confirmables(allOtherNodes);
  }

  void acknowledgeConfirmation(final String trackingId, final Node node) {
    confirmables.confirm(trackingId, node);
  }

  Collection<String> allTrackingIds() {
    return confirmables.allTrackingIds();
  }

  void distribute(final AttributeSet set) {
    distributeTo(set, allOtherNodes);
  }

  void distributeTo(final AttributeSet set, final Collection<Node> nodes) {
    final CreateAttributeSet create = new CreateAttributeSet(node, set);
    final Confirmable confirmable = confirmables.unconfirmedFor(create, nodes);
    outbound.application(ApplicationSays.from(node.id(), node.name(), create.toPayload()), confirmable.unconfirmedNodes());
    application.informAttributeSetCreated(set.name);
    
    for (final TrackedAttribute tracked : set.all()) {
      distributeTo(set, tracked, ApplicationMessageType.AddAttribute, nodes);
    }
  }

  void distribute(final AttributeSet set, final TrackedAttribute tracked, final ApplicationMessageType type) {
    distributeTo(set, tracked, type, allOtherNodes);
  }

  void distributeTo(final AttributeSet set, final TrackedAttribute tracked, final ApplicationMessageType type, final Collection<Node> nodes) {
    switch (type) {
    case AddAttribute:
      final AddAttribute add = AddAttribute.from(node, set, tracked);
      final Confirmable addConfirmable = confirmables.unconfirmedFor(add, nodes);
      outbound.application(ApplicationSays.from(node.id(), node.name(), add.toPayload()), addConfirmable.unconfirmedNodes());
      application.informAttributeAdded(set.name, tracked.attribute.name);
      break;
    case RemoveAttribute:
      final RemoveAttribute remove = RemoveAttribute.from(node, set, tracked);
      final Confirmable removeConfirmable = confirmables.unconfirmedFor(remove, nodes);
      outbound.application(ApplicationSays.from(node.id(), node.name(), remove.toPayload()), removeConfirmable.unconfirmedNodes());
      application.informAttributeRemoved(set.name, tracked.attribute.name);
      break;
    case ReplaceAttribute:
      final ReplaceAttribute replace = ReplaceAttribute.from(node, set, tracked);
      final Confirmable replaceConfirmable = confirmables.unconfirmedFor(replace, nodes);
      outbound.application(ApplicationSays.from(node.id(), node.name(), replace.toPayload()), replaceConfirmable.unconfirmedNodes());
      application.informAttributeReplaced(set.name, tracked.attribute.name);
      break;
    default:
      throw new IllegalArgumentException("Cannot distribute unknown ApplicationMessageType.");
    }
  }

  void confirm(
          final String correlatingMessageId,
          final AttributeSet set,
          final Node toOriginalSource) {
    
    final ConfirmAttributeSet confirm = new ConfirmAttributeSet(correlatingMessageId, node, set);
    outbound.application(ApplicationSays.from(node.id(), node.name(), confirm.toPayload()), toOriginalSource.collected());
    application.informAttributeSetCreated(set.name);
  }
  
  void confirm(
          final String correlatingMessageId,
          final AttributeSet set,
          final TrackedAttribute tracked,
          final ApplicationMessageType type,
          final Node toOriginalSource) {
    
    switch (type) {
    case AddAttribute:
      final ConfirmAttribute confirmAdd = ConfirmAttribute.from(correlatingMessageId, toOriginalSource, set, tracked, ApplicationMessageType.ConfirmAddAttribute);
      outbound.application(ApplicationSays.from(node.id(), node.name(), confirmAdd.toPayload()), toOriginalSource.collected());
      application.informAttributeAdded(set.name, tracked.attribute.name);
      break;
    case RemoveAttribute:
      final ConfirmAttribute confirmRemove = ConfirmAttribute.from(correlatingMessageId, toOriginalSource, set, tracked, ApplicationMessageType.ConfirmRemoveAttribute);
      outbound.application(ApplicationSays.from(node.id(), node.name(), confirmRemove.toPayload()), toOriginalSource.collected());
      application.informAttributeRemoved(set.name, tracked.attribute.name);
      break;
    case ReplaceAttribute:
      final ConfirmAttribute confirmReplace = ConfirmAttribute.from(correlatingMessageId, toOriginalSource, set, tracked, ApplicationMessageType.ConfirmReplaceAttribute);
      outbound.application(ApplicationSays.from(node.id(), node.name(), confirmReplace.toPayload()), toOriginalSource.collected());
      application.informAttributeReplaced(set.name, tracked.attribute.name);
      break;
    default:
      throw new IllegalArgumentException("Cannot confirm unknown ApplicationMessageType.");
    }
  }

  void redistributeUnconfirmed() {
    for (final Confirmable confirmable : confirmables.allRedistributable()) {
      logger.log("REDIST: " + confirmable);
      outbound.application(ApplicationSays.from(
              node.id(), node.name(),
              confirmable.message().toPayload()),
              confirmable.unconfirmedNodes());
    }
  }

  void synchronizeTo(final Collection<AttributeSet> sets, final Node targetNode) {
    final Collection<Node> onlyOneTargetNode = targetNode.collected();
    
    for (final AttributeSet set : sets) {
      this.distributeTo(set, onlyOneTargetNode);
    }
  }

  Collection<Node> unconfirmedNodesFor(final String trackingId) {
    return confirmables.confirmableOf(trackingId).unconfirmedNodes();
  }
}