/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.discovery;

import org.neo4j.coreedge.server.AdvertisedSocketAddress;
import org.neo4j.coreedge.server.BoltAddress;
import org.neo4j.coreedge.server.CoreEdgeClusterSettings;
import org.neo4j.coreedge.server.CoreMember;
import org.neo4j.coreedge.server.edge.EnterpriseEdgeEditionModule;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

import static org.neo4j.helpers.Listeners.notifyListeners;
import static org.neo4j.helpers.collection.Iterables.firstOrNull;

class TestOnlyCoreTopologyService extends LifecycleAdapter implements CoreTopologyService
{
    private final CoreMember me;
    private final TestOnlyDiscoveryServiceFactory cluster;
    private final BoltAddress meBolt;

    TestOnlyCoreTopologyService( Config config, TestOnlyDiscoveryServiceFactory cluster )
    {
        this.cluster = cluster;
        synchronized ( this.cluster )
        {
            this.me = toCoreMember( config );
            cluster.coreMembers.add( me );

            meBolt = extractBoltAddress( config );
            cluster.boltAddresses.add( meBolt  );

            if ( cluster.bootstrappable == null )
            {
                cluster.bootstrappable = me;
            }

            notifyListeners( cluster.membershipListeners, listener ->
                    listener.onTopologyChange( currentTopology() ) );
        }
    }

    private CoreMember toCoreMember( Config config )
    {
        return new CoreMember(
                config.get( CoreEdgeClusterSettings.transaction_advertised_address ),
                config.get( CoreEdgeClusterSettings.raft_advertised_address ),
                new AdvertisedSocketAddress(  EnterpriseEdgeEditionModule.extractBoltAddress( config  ).toString())
        );
    }

    private BoltAddress extractBoltAddress( Config config )
    {
        return new BoltAddress(
                new AdvertisedSocketAddress(
                        EnterpriseEdgeEditionModule.extractBoltAddress( config ).toString() ) );
    }

    @Override
    public void addMembershipListener( Listener listener )
    {
        synchronized ( cluster )
        {
            cluster.membershipListeners.add( listener );
            listener.onTopologyChange( currentTopology() );
        }
    }

    @Override
    public void removeMembershipListener( Listener listener )
    {
        synchronized ( cluster )
        {
            cluster.membershipListeners.remove( listener );
        }
    }

    @Override
    public void start() throws Throwable
    {
        notifyListeners( cluster.membershipListeners, listener -> listener.onTopologyChange( currentTopology() ) );
    }

    @Override
    public void stop()
    {
        synchronized ( cluster )
        {
            cluster.coreMembers.remove( me );
            cluster.boltAddresses.remove( meBolt );

            // Move the bootstrappable instance, if necessary
            if ( cluster.bootstrappable == me )
            {
                cluster.bootstrappable = firstOrNull( cluster.coreMembers );
            }

            notifyListeners( cluster.membershipListeners, listener -> listener.onTopologyChange( currentTopology() ) );
        }
    }

    @Override
    public ClusterTopology currentTopology()
    {
        CoreMember firstMember = firstOrNull( cluster.coreMembers );
        return new TestOnlyClusterTopology( firstMember != null && firstMember.equals( me ),
                cluster.coreMembers, cluster.boltAddresses, cluster.edgeMembers );
    }
}
