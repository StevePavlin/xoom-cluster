// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.cluster.model.attribute;

import io.vlingo.actors.ActorInstantiator;
import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.actors.Stoppable;
import io.vlingo.cluster.model.application.ClusterApplication;
import io.vlingo.cluster.model.outbound.OperationalOutboundStream;
import io.vlingo.common.Scheduled;
import io.vlingo.wire.fdx.inbound.InboundStreamInterest;
import io.vlingo.wire.node.Configuration;
import io.vlingo.wire.node.Node;
import io.vlingo.wire.node.NodeSynchronizer;

public interface AttributesAgent extends AttributesCommands, NodeSynchronizer, InboundStreamInterest, Scheduled<Object>, Stoppable {
  static AttributesAgent instance(
      final Stage stage,
      final Node node,
      final ClusterApplication application,
      final OperationalOutboundStream outbound,
      final Configuration configuration) {

    final Definition definition =
            new Definition(
                    AttributesAgentActor.class,
                    new AttributesAgentInstantiator(node, application, outbound, configuration),
                    "attributes-agent");

    return stage.actorFor(AttributesAgent.class, definition);
  }

  class AttributesAgentInstantiator implements ActorInstantiator<AttributesAgentActor> {
    private static final long serialVersionUID = 3269867041246996465L;

    private final Node node;
    private final ClusterApplication application;
    private final OperationalOutboundStream outbound;
    private final Configuration configuration;

    public AttributesAgentInstantiator(
            final Node node,
            final ClusterApplication application,
            final OperationalOutboundStream outbound,
            final Configuration configuration) {
      this.node = node;
      this.application = application;
      this.outbound = outbound;
      this.configuration = configuration;
    }

    @Override
    public AttributesAgentActor instantiate() {
      return new AttributesAgentActor(node, application, outbound, configuration);
    }

    @Override
    public Class<AttributesAgentActor> type() {
      return AttributesAgentActor.class;
    }
  }
}
