/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.node.network_load;

import bisq.network.p2p.message.NetworkEnvelope;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Getter
@ToString
public class ConnectionMetrics {
    private final long created;
    private final AtomicLong lastUpdate = new AtomicLong();
    private final TreeMap<Integer, AtomicLong> numMessagesSentPerMinute = new TreeMap<>();
    private final TreeMap<Integer, AtomicLong> sentBytesPerMinute = new TreeMap<>();
    private final TreeMap<Integer, AtomicLong> spentSendMessageTimePerMinute = new TreeMap<>();
    private final TreeMap<Integer, AtomicLong> deserializeTimePerMinute = new TreeMap<>();
    private final TreeMap<Integer, AtomicLong> numMessagesReceivedPerMinute = new TreeMap<>();
    private final TreeMap<Integer, AtomicLong> receivedBytesPerMinute = new TreeMap<>();

    private final AtomicLong numMessagesReceived = new AtomicLong();
    private final List<Long> rrtList = new CopyOnWriteArrayList<>();

    public ConnectionMetrics() {
        created = new Date().getTime();
    }

    public Date getCreationDate() {
        return new Date(created);
    }

    public long getAge() {
        return System.currentTimeMillis() - created;
    }

    public void onSent(NetworkEnvelope networkEnvelope, long spentTime) {
        long now = System.currentTimeMillis();
        lastUpdate.set(now);

        int ageInMinutes = getAgeInMinutes(now);
        sentBytesPerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        sentBytesPerMinute.get(ageInMinutes).getAndAdd(networkEnvelope.toProto().getSerializedSize());

        numMessagesSentPerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        numMessagesSentPerMinute.get(ageInMinutes).incrementAndGet();

        spentSendMessageTimePerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        spentSendMessageTimePerMinute.get(ageInMinutes).getAndAdd(spentTime);
    }

    public void onReceived(NetworkEnvelope networkEnvelope, long deserializeTime) {
        long now = System.currentTimeMillis();
        lastUpdate.set(now);

        int ageInMinutes = getAgeInMinutes(now);
        receivedBytesPerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        receivedBytesPerMinute.get(ageInMinutes).getAndAdd(networkEnvelope.toProto().getSerializedSize());

        numMessagesReceivedPerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        numMessagesReceivedPerMinute.get(ageInMinutes).incrementAndGet();

        deserializeTimePerMinute.putIfAbsent(ageInMinutes, new AtomicLong());
        deserializeTimePerMinute.get(ageInMinutes).getAndAdd(deserializeTime);
    }

    public void addRtt(long value) {
        this.rrtList.add(value);
    }

    public double getAverageRtt() {
        return rrtList.stream().mapToLong(e -> e).average().orElse(0d);
    }

    public long getSentBytes() {
        return sumOf(sentBytesPerMinute);
    }

    public long getNumMessagesSent() {
        return sumOf(numMessagesSentPerMinute);
    }

    public long getSpentSendMessageTimePerMinute() {
        return sumOf(spentSendMessageTimePerMinute);
    }

    public long getReceivedBytes() {
        return sumOf(receivedBytesPerMinute);
    }

    public long getNumMessagesReceived() {
        return sumOf(numMessagesReceivedPerMinute);
    }

    public long getDeserializeTimePerMinute() {
        return sumOf(deserializeTimePerMinute);
    }

    public long getNumMessagesSentOfLastHour() {
        return getNumMessagesSentOfLastMinutes(60);
    }

    public long getSentBytesOfLastHour() {
        return getSentBytesOfLastMinutes(60);
    }

    public long getSpentSendMessageTimeOfLastHour() {
        return getSpentSendMessageTimeOfLastMinutes(60);
    }

    public long getReceivedBytesOfLastHour() {
        return getReceivedBytesOfLastMinutes(60);
    }

    public long getDeserializeTimeOfLastHour() {
        return getDeserializeTimeOfLastMinutes(60);
    }

    public long getNumMessagesReceivedOfLastHour() {
        return getNumMessagesReceivedOfLastMinutes(60);
    }

    public long getNumMessagesSentOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(numMessagesSentPerMinute, lastMinutes);
    }

    public long getSentBytesOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(sentBytesPerMinute, lastMinutes);
    }

    public long getSpentSendMessageTimeOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(spentSendMessageTimePerMinute, lastMinutes);
    }

    public long getNumMessagesReceivedOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(numMessagesReceivedPerMinute, lastMinutes);
    }

    public long getReceivedBytesOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(receivedBytesPerMinute, lastMinutes);
    }

    public long getDeserializeTimeOfLastMinutes(int lastMinutes) {
        return sumOfLastMinute(deserializeTimePerMinute, lastMinutes);
    }

    private long sumOf(TreeMap<Integer, AtomicLong> treeMap) {
        return treeMap.values().stream().mapToLong(AtomicLong::get).sum();
    }

    private long sumOfLastMinute(TreeMap<Integer, AtomicLong> treeMap, int lastMinutes) {
        // The Treemap returns the values in ascending order of the corresponding keys.
        int from = Math.max(0, treeMap.size() - lastMinutes);
        List<AtomicLong> list = new ArrayList<>(treeMap.values()).subList(from, treeMap.size());
        return list.stream().mapToLong(AtomicLong::get).sum();
    }

    private int getAgeInMinutes(long now) {
        return (int) (now - created) / 60000;
    }
}