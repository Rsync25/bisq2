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

package bisq.network.p2p.node.envelope;

import bisq.network.p2p.message.NetworkEnvelope;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class NetworkEnvelopeSocket implements Closeable {
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public NetworkEnvelopeSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public void send(NetworkEnvelope networkEnvelope) throws IOException {
        bisq.network.protobuf.NetworkEnvelope proto = checkNotNull(networkEnvelope.toProto(),
                "networkEnvelope.toProto() must not be null");
        proto.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    public bisq.network.protobuf.NetworkEnvelope receiveNextEnvelope() throws IOException {
        return bisq.network.protobuf.NetworkEnvelope.parseDelimitedFrom(inputStream);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
