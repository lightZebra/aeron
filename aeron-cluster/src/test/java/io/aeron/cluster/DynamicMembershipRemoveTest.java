package io.aeron.cluster;

import org.junit.Test;

public class DynamicMembershipRemoveTest
{
    @Test(timeout = 10_000)
    public void shouldRemoveDynamicNodeThenReceiveMessages() throws Exception
    {
        try (TestCluster cluster = TestCluster.startCluster(3, 1))
        {
            cluster.awaitLeader();

            final TestNode dynamicMember = cluster.startDynamicNode(3, true);

            Thread.sleep(1_000);

            cluster.stopNode(dynamicMember);

            Thread.sleep(1_000);

            cluster.connectClient();
            final int messageCount = 10;
            cluster.sendMessage(messageCount);
            cluster.awaitResponses(messageCount);
        }
    }
}
