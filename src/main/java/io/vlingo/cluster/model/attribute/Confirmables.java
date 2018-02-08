// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.cluster.model.attribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.vlingo.cluster.model.Properties;
import io.vlingo.cluster.model.attribute.message.ApplicationMessage;
import io.vlingo.wire.node.Node;

final class Confirmables {
  private final Collection<Node> allOtherNodes;
  private final List<Confirmable> expectedConfirmables;

  Confirmables(final Collection<Node> allOtherNodes) {
    this.allOtherNodes = allOtherNodes;
    this.expectedConfirmables = new ArrayList<>();
  }

  Collection<Confirmable> allRedistributable() {
    final List<Confirmable> ready = new ArrayList<>();
    for (final Confirmable confirmable : expectedConfirmables) {
      if (confirmable.isRedistributableAsOf()) {
        ready.add(confirmable);
      }
    }
    return ready;
  }

  Collection<String> allTrackingIds() {
    final List<String> all = new ArrayList<>();
    
    for (final Confirmable confirmable : expectedConfirmables) {
      all.add(confirmable.message.trackingId);
    }
    
    return all;
  }

  void confirm(final String trackingId, final Node node) {
    final Confirmable confirmable = confirmableOf(trackingId);
    confirmable.confirm(node);
    if (!confirmable.hasUnconfirmedNodes()) {
      expectedConfirmables.remove(confirmable);
    }
  }

  Confirmable confirmableOf(final String trackingId) {
    for (final Confirmable confirmable : expectedConfirmables) {
      if (confirmable.trackingId.equals(trackingId)) {
        return confirmable;
      }
    }
    return Confirmable.NoConfirmable;
  }

  Confirmable unconfirmed(final ApplicationMessage message) {
    return unconfirmedFor(message, allOtherNodes);
  }

  Confirmable unconfirmedFor(final ApplicationMessage message, final Collection<Node> nodes) {
    final Confirmable confirmable = new Confirmable(message, nodes);
    expectedConfirmables.add(confirmable);
    return confirmable;
  }

  static final class Confirmable {
    static final Confirmable NoConfirmable = new Confirmable();
    
    private final ApplicationMessage message;
    private final List<Node> unconfirmedNodes;
    private final long createdOn;
    private final String trackingId;
    
    Confirmable(final ApplicationMessage message, final Collection<Node> allOtherNodes) {
      this.message = message;
      this.unconfirmedNodes = new ArrayList<>();
      this.unconfirmedNodes.addAll(allOtherNodes);
      this.createdOn = System.currentTimeMillis();
      this.trackingId = message.trackingId;
    }

    private Confirmable() {
      this.message = null;
      this.unconfirmedNodes = new ArrayList<>(0);
      this.createdOn = 0L;
      this.trackingId = "";
    }

    void confirm(final Node node) {
      unconfirmedNodes.remove(node);
    }

    boolean hasUnconfirmedNodes() {
      return !unconfirmedNodes.isEmpty();
    }

    ApplicationMessage message() {
      return message;
    }

    boolean isRedistributableAsOf() {
      final long targetTime = createdOn + Properties.instance.clusterAttributesRedistributionInterval();
      return targetTime < System.currentTimeMillis();
    }

    Collection<Node> unconfirmedNodes() {
      return unconfirmedNodes;
    }
    
    @Override
    public boolean equals(final Object other) {
      if (other == null || other.getClass() != Confirmable.class) {
        return false;
      }
      
      return this.trackingId.equals(((Confirmable)other).trackingId);
    }
    
    @Override
    public String toString() {
      return "Confirmable[trackingId=" + trackingId + " nodes=" + unconfirmedNodes + "]";
    }
  }
}