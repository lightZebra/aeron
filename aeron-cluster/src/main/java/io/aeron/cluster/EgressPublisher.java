/*
 * Copyright 2014-2019 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.codecs.*;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.ExpandableArrayBuffer;

import static io.aeron.cluster.ClusterSession.MAX_ENCODED_MEMBERSHIP_QUERY_LENGTH;

class EgressPublisher
{
    private static final int SEND_ATTEMPTS = 3;

    private final BufferClaim bufferClaim = new BufferClaim();
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(MAX_ENCODED_MEMBERSHIP_QUERY_LENGTH);
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final SessionEventEncoder sessionEventEncoder = new SessionEventEncoder();
    private final ChallengeEncoder challengeEncoder = new ChallengeEncoder();
    private final NewLeaderEventEncoder newLeaderEventEncoder = new NewLeaderEventEncoder();

    boolean sendEvent(
        final ClusterSession session,
        final long leadershipTermId,
        final int leaderMemberId,
        final EventCode code,
        final String detail)
    {
        final int length = MessageHeaderEncoder.ENCODED_LENGTH +
            SessionEventEncoder.BLOCK_LENGTH +
            SessionEventEncoder.detailHeaderLength() +
            detail.length();

        int attempts = SEND_ATTEMPTS;
        do
        {
            final long result = session.tryClaim(length, bufferClaim);
            if (result > 0)
            {
                sessionEventEncoder
                    .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
                    .clusterSessionId(session.id())
                    .correlationId(session.correlationId())
                    .leadershipTermId(leadershipTermId)
                    .leaderMemberId(leaderMemberId)
                    .code(code)
                    .version(AeronCluster.Configuration.PROTOCOL_SEMANTIC_VERSION)
                    .detail(detail);

                bufferClaim.commit();

                return true;
            }
        }
        while (--attempts > 0);

        return false;
    }

    boolean sendChallenge(final ClusterSession session, final byte[] encodedChallenge)
    {
        if (!session.isResponsePublicationConnected())
        {
            return false;
        }

        challengeEncoder
            .wrapAndApplyHeader(buffer, 0, messageHeaderEncoder)
            .clusterSessionId(session.id())
            .correlationId(session.correlationId())
            .putEncodedChallenge(encodedChallenge, 0, encodedChallenge.length);

        final int length = MessageHeaderEncoder.ENCODED_LENGTH + challengeEncoder.encodedLength();

        int attempts = SEND_ATTEMPTS;
        do
        {
            final long result = session.offer(buffer, 0, length);
            if (result > 0)
            {
                return true;
            }
        }
        while (--attempts > 0);

        return false;
    }

    boolean newLeader(
        final ClusterSession session,
        final long leadershipTermId,
        final int leaderMemberId,
        final String memberEndpoints)
    {
        final int length = MessageHeaderEncoder.ENCODED_LENGTH +
            NewLeaderEventEncoder.BLOCK_LENGTH +
            NewLeaderEventEncoder.memberEndpointsHeaderLength() +
            memberEndpoints.length();

        int attempts = SEND_ATTEMPTS;
        do
        {
            final long result = session.tryClaim(length, bufferClaim);
            if (result > 0)
            {
                newLeaderEventEncoder
                    .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
                    .clusterSessionId(session.id())
                    .leadershipTermId(leadershipTermId)
                    .leaderMemberId(leaderMemberId)
                    .memberEndpoints(memberEndpoints);

                bufferClaim.commit();

                return true;
            }
        }
        while (--attempts > 0);

        return false;
    }
}
