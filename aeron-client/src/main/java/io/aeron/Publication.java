/*
 * Copyright 2014-2025 Real Logic Limited.
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
package io.aeron;

import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FrameDescriptor;
import io.aeron.logbuffer.HeaderWriter;
import io.aeron.logbuffer.LogBufferDescriptor;
import io.aeron.status.ChannelEndpointStatus;
import io.aeron.status.LocalSocketAddressStatus;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.ReadablePosition;

import java.util.List;

import static io.aeron.logbuffer.FrameDescriptor.FRAME_ALIGNMENT;
import static io.aeron.logbuffer.LogBufferDescriptor.*;
import static io.aeron.protocol.DataHeaderFlyweight.HEADER_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.align;

/**
 * Aeron publisher API for sending messages to subscribers of a given channel and streamId pair. {@link Publication}s
 * are created via the {@link Aeron#addPublication(String, int)} {@link Aeron#addExclusivePublication(String, int)}
 * methods, and messages are sent via one of the {@link #offer(DirectBuffer)} methods.
 * <p>
 * The APIs used for tryClaim and offer are non-blocking.
 * <p>
 * <b>Note:</b> All methods are threadsafe except offer and tryClaim for the subclass
 * {@link ExclusivePublication}. In the case of {@link ConcurrentPublication} all methods are threadsafe.
 *
 * Aeron发布者API，用于向给定通道和流ID对的订阅者发送消息。{@link Publication}通过{@link Aeron#addPublication(String, int)} {@link Aeron#addExclusivePublication(String, int)}
 * 方法创建，并通过{@link #offer(DirectBuffer)}方法之一发送消息。
 * <p>
 * 用于tryClaim和offer的API是非阻塞的。
 * <p>
 * <b>注意：</b> 所有方法都是线程安全的，除了子类{@link ExclusivePublication}的offer和tryClaim方法。
 * 对于{@link ConcurrentPublication}，所有方法都是线程安全的。
 *
 * @see ConcurrentPublication
 * @see ExclusivePublication
 * @see Aeron#addPublication(String, int)
 * @see Aeron#addExclusivePublication(String, int)
 */
public abstract class Publication implements AutoCloseable
{
    /**
     * The publication is not connected to a subscriber, this can be an intermittent state as subscribers come and go.
     * 
     * 出版物未连接到订阅者，这可能是订阅者来去时的间歇状态。
     */
    public static final long NOT_CONNECTED = -1;

    /**
     * The offer failed due to back pressure from the subscribers preventing further transmission.
     * 
     * 由于来自订阅者的背压阻止了进一步传输，导致发布失败。
     */
    public static final long BACK_PRESSURED = -2;

    /**
     * The offer failed due to an administration action and should be retried.
     * The action is an operation such as log rotation which is likely to have succeeded by the next retry attempt.
     * 
     * 由于管理操作导致发布失败，应重试。
     * 此操作是诸如日志轮换之类的操作，可能在下一次重试尝试时成功。
     */
    public static final long ADMIN_ACTION = -3;

    /**
     * The {@link Publication} has been closed and should no longer be used.
     * 
     * {@link Publication}已关闭，不应再使用。
     */
    public static final long CLOSED = -4;

    /**
     * The offer failed due to reaching the maximum position of the stream given term buffer length times the total
     * possible number of terms.
     * <p>
     * If this happens then the publication should be closed and a new one added. To make it less likely to happen then
     * increase the term buffer length.
     * 
     * 由于达到给定项缓冲区长度乘以总可能项数的最大流位置，导致发布失败。
     * <p>
     * 如果发生这种情况，则应关闭出版物并添加新的出版物。为了减少这种情况的发生，
     * 增加项缓冲区长度。
     */
    public static final long MAX_POSITION_EXCEEDED = -5;

    // 以下是Publication类的成员变量，存储了与发布相关的各种信息
    final long originalRegistrationId;  // 初始注册ID
    final long registrationId;          // 注册ID
    final long maxPossiblePosition;     // 最大可能位置
    final int channelStatusId;          // 通道状态ID
    final int streamId;                 // 流ID
    final int sessionId;                // 会话ID
    final int maxMessageLength;         // 最大消息长度
    final int maxFramedLength;          // 最大帧长度
    final int initialTermId;            // 初始任期ID
    final int maxPayloadLength;         // 最大负载长度
    final int positionBitsToShift;      // 位置移位位数
    final int termBufferLength;         // 任期缓冲区长度
    volatile boolean isClosed = false;  // 是否已关闭
    boolean revokeOnClose = false;      // 关闭时是否撤销

    // 以下是与日志缓冲区和客户端导体相关的成员变量
    final ReadablePosition positionLimit;     // 位置限制
    final UnsafeBuffer[] termBuffers;         // 任期缓冲区数组
    final UnsafeBuffer logMetaDataBuffer;     // 日志元数据缓冲区
    final HeaderWriter headerWriter;          // 头部写入器
    final LogBuffers logBuffers;              // 日志缓冲区
    final ClientConductor conductor;          // 客户端导体
    final String channel;                     // 通道

    // Publication构造函数，初始化Publication实例的各种属性
    Publication(
        final ClientConductor clientConductor,  // 客户端导体
        final String channel,                   // 通道
        final int streamId,                     // 流ID
        final int sessionId,                    // 会话ID
        final ReadablePosition positionLimit,   // 位置限制
        final int channelStatusId,              // 通道状态ID
        final LogBuffers logBuffers,            // 日志缓冲区
        final long originalRegistrationId,      // 初始注册ID
        final long registrationId)              // 注册ID
    {
        // 初始化日志元数据缓冲区
        final UnsafeBuffer logMetaDataBuffer = logBuffers.metaDataBuffer();
        // 设置任期缓冲区长度
        this.termBufferLength = logBuffers.termLength();
        // 计算最大消息长度
        this.maxMessageLength = FrameDescriptor.computeMaxMessageLength(termBufferLength);
        // 计算最大负载长度
        this.maxPayloadLength = LogBufferDescriptor.mtuLength(logMetaDataBuffer) - HEADER_LENGTH;
        // 计算最大帧长度
        this.maxFramedLength = computeFragmentedFrameLength(maxMessageLength, maxPayloadLength);
        // 计算最大可能位置
        this.maxPossiblePosition = termBufferLength * (1L << 31);
        // 设置客户端导体
        this.conductor = clientConductor;
        // 设置通道
        this.channel = channel;
        // 设置流ID
        this.streamId = streamId;
        // 设置会话ID
        this.sessionId = sessionId;
        // 设置初始任期ID
        this.initialTermId = LogBufferDescriptor.initialTermId(logMetaDataBuffer);
        // 设置任期缓冲区数组
        this.termBuffers = logBuffers.duplicateTermBuffers();
        // 设置日志元数据缓冲区
        this.logMetaDataBuffer = logMetaDataBuffer;
        // 设置日志缓冲区
        this.logBuffers = logBuffers;
        // 设置初始注册ID
        this.originalRegistrationId = originalRegistrationId;
        // 设置注册ID
        this.registrationId = registrationId;
        // 设置位置限制
        this.positionLimit = positionLimit;
        // 设置通道状态ID
        this.channelStatusId = channelStatusId;
        // 设置位置移位位数
        this.positionBitsToShift = LogBufferDescriptor.positionBitsToShift(termBufferLength);
        // 创建头部写入器实例
        this.headerWriter = HeaderWriter.newInstance(defaultFrameHeader(logMetaDataBuffer));

        // 检查每个分区的尾计数器偏移量是否在范围内
        for (int i = 0; i < PARTITION_COUNT; i++)
        {
            final int tailCounterOffset = TERM_TAIL_COUNTERS_OFFSET + (i * SIZE_OF_LONG);
            logMetaDataBuffer.boundsCheck(tailCounterOffset, SIZE_OF_LONG);
        }
    }

    /**
     * Number of bits to right shift a position to get a term count for how far the stream has progressed.
     *
     * @return of bits to right shift a position to get a term count for how far the stream has progressed.
     */
    public int positionBitsToShift()
    {
        return positionBitsToShift;
    }

    /**
     * Get the length in bytes for each term partition in the log buffer.
     *
     * @return the length in bytes for each term partition in the log buffer.
     */
    public int termBufferLength()
    {
        return termBufferLength;
    }

    /**
     * The maximum possible position this stream can reach due to its term buffer length.
     * <p>
     * Maximum possible position is term-length times 2^31 in bytes.
     *
     * @return the maximum possible position this stream can reach due to it term buffer length.
     */
    public long maxPossiblePosition()
    {
        return maxPossiblePosition;
    }

    /**
     * Media address for delivery to the channel.
     *
     * @return Media address for delivery to the channel.
     */
    public String channel()
    {
        return channel;
    }

    /**
     * Stream identity for scoping within the channel media address.
     *
     * @return Stream identity for scoping within the channel media address.
     */
    public int streamId()
    {
        return streamId;
    }

    /**
     * Session under which messages are published. Identifies this Publication instance. Sessions are unique across
     * all active publications on a driver instance.
     *
     * @return the session id for this publication.
     */
    public int sessionId()
    {
        return sessionId;
    }

    /**
     * The initial term id assigned when this {@link Publication} was created. This can be used to determine how many
     * terms have passed since creation.
     *
     * @return the initial term id.
     */
    public int initialTermId()
    {
        return initialTermId;
    }

    /**
     * Maximum message length supported in bytes. Messages may be made of multiple fragments if greater than
     * MTU length.
     *
     * @return maximum message length supported in bytes.
     */
    public int maxMessageLength()
    {
        return maxMessageLength;
    }

    /**
     * Maximum length of a message payload that fits within a message fragment.
     * <p>
     * This is the MTU length minus the message fragment header length.
     *
     * @return maximum message fragment payload length.
     */
    public int maxPayloadLength()
    {
        return maxPayloadLength;
    }

    /**
     * Get the registration used to register this Publication with the media driver by the first publisher.
     *
     * @return original registration id
     */
    public long originalRegistrationId()
    {
        return originalRegistrationId;
    }

    /**
     * Is this Publication the original instance added to the driver? If not then it was added after another client
     * has already added the publication.
     *
     * @return true if this instance is the first added otherwise false.
     */
    public boolean isOriginal()
    {
        return originalRegistrationId == registrationId;
    }

    /**
     * Get the registration id used to register this Publication with the media driver.
     * <p>
     * If this value is different from the {@link #originalRegistrationId()} then a previous active registration exists.
     *
     * @return registration id
     */
    public long registrationId()
    {
        return registrationId;
    }

    /**
     * Has the {@link Publication} seen an active Subscriber recently?
     *
     * @return true if this {@link Publication} has recently seen an active subscriber otherwise false.
     */
    public boolean isConnected()
    {
        return !isClosed && LogBufferDescriptor.isConnected(logMetaDataBuffer);
    }

    /**
     * Remove resources used by this Publication when there are no more references.
     * <p>
     * Publications are reference counted and are only truly closed when the ref count reaches zero.
     */
    public void close()
    {
        if (!isClosed)
        {
            conductor.removePublication(this);
        }
    }

    /**
     * Has this object been closed and should no longer be used?
     *
     * @return true if it has been closed otherwise false.
     */
    public boolean isClosed()
    {
        return isClosed;
    }

    /**
     * Get the status of the media channel for this Publication.
     * <p>
     * The status will be {@link io.aeron.status.ChannelEndpointStatus#ERRORED} if a socket exception occurs on setup
     * and {@link io.aeron.status.ChannelEndpointStatus#ACTIVE} if all is well.
     *
     * @return status for the channel as one of the constants from {@link ChannelEndpointStatus} with it being
     * {@link ChannelEndpointStatus#NO_ID_ALLOCATED} if the publication is closed.
     * @see io.aeron.status.ChannelEndpointStatus
     */
    public long channelStatus()
    {
        if (isClosed)
        {
            return ChannelEndpointStatus.NO_ID_ALLOCATED;
        }

        return conductor.channelStatus(channelStatusId);
    }

    /**
     * Get the counter used to represent the channel status for this publication.
     *
     * @return the counter used to represent the channel status for this publication.
     */
    public int channelStatusId()
    {
        return channelStatusId;
    }

    /**
     * Fetches the local socket address for this publication. If the channel is not
     * {@link io.aeron.status.ChannelEndpointStatus#ACTIVE}, then this will return an empty list.
     * <p>
     * The format is as follows:
     * <br>
     * <br>
     * IPv4: <code>ip address:port</code>
     * <br>
     * IPv6: <code>[ip6 address]:port</code>
     * <br>
     * <br>
     * This is to match the formatting used in the Aeron URI. For publications this will be the control address and
     * is likely to only contain a single entry.
     *
     * @return local socket addresses for this publication.
     * @see #channelStatus()
     */
    public List<String> localSocketAddresses()
    {
        return LocalSocketAddressStatus.findAddresses(conductor.countersReader(), channelStatus(), channelStatusId);
    }

    /**
     * Get the current position to which the publication has advanced for this stream.
     *
     * @return the current position to which the publication has advanced for this stream or {@link #CLOSED}.
     */
    public long position()
    {
        if (isClosed)
        {
            return CLOSED;
        }

        final long rawTail = rawTailVolatile(logMetaDataBuffer);
        final int termOffset = termOffset(rawTail, termBufferLength);

        return computePosition(termId(rawTail), termOffset, positionBitsToShift, initialTermId);
    }

    /**
     * Get the position limit beyond which this {@link Publication} will be back pressured.
     * <p>
     * This should only be used as a guide to determine when back pressure is likely to be applied.
     *
     * @return the position limit beyond which this {@link Publication} will be back pressured.
     */
    public long positionLimit()
    {
        if (isClosed)
        {
            return CLOSED;
        }

        return positionLimit.getVolatile();
    }

    /**
     * Get the counter id for the position limit after which the publication will be back pressured.
     *
     * @return the counter id for the position limit after which the publication will be back pressured.
     */
    public int positionLimitId()
    {
        return positionLimit.id();
    }

    /**
     * Available window for offering into a publication before the {@link #positionLimit()} is reached.
     *
     * @return window for offering into a publication before the {@link #positionLimit()} is reached. If
     * the publication is closed then {@link #CLOSED} will be returned.
     */
    public abstract long availableWindow();

    /**
     * Non-blocking publish of a buffer containing a message.
     *
     * @param buffer containing message.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public final long offer(final DirectBuffer buffer)
    {
        return offer(buffer, 0, buffer.capacity());
    }

    /**
     * Non-blocking publish of a partial buffer containing a message.
     *
     * @param buffer containing message.
     * @param offset offset in the buffer at which the encoded message begins.
     * @param length in bytes of the encoded message.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public final long offer(final DirectBuffer buffer, final int offset, final int length)
    {
        return offer(buffer, offset, length, null);
    }

    /**
     * Non-blocking publish of a partial buffer containing a message.
     *
     * @param buffer                containing message.
     * @param offset                offset in the buffer at which the encoded message begins.
     * @param length                in bytes of the encoded message.
     * @param reservedValueSupplier {@link ReservedValueSupplier} for the frame.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public abstract long offer(
        DirectBuffer buffer, int offset, int length, ReservedValueSupplier reservedValueSupplier);

    /**
     * Non-blocking publish of a message composed of two parts, e.g. a header and encapsulated payload.
     *
     * @param bufferOne containing the first part of the message.
     * @param offsetOne at which the first part of the message begins.
     * @param lengthOne of the first part of the message.
     * @param bufferTwo containing the second part of the message.
     * @param offsetTwo at which the second part of the message begins.
     * @param lengthTwo of the second part of the message.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public final long offer(
        final DirectBuffer bufferOne,
        final int offsetOne,
        final int lengthOne,
        final DirectBuffer bufferTwo,
        final int offsetTwo,
        final int lengthTwo)
    {
        return offer(bufferOne, offsetOne, lengthOne, bufferTwo, offsetTwo, lengthTwo, null);
    }

    /**
     * Non-blocking publish of a message composed of two parts, e.g. a header and encapsulated payload.
     *
     * @param bufferOne             containing the first part of the message.
     * @param offsetOne             at which the first part of the message begins.
     * @param lengthOne             of the first part of the message.
     * @param bufferTwo             containing the second part of the message.
     * @param offsetTwo             at which the second part of the message begins.
     * @param lengthTwo             of the second part of the message.
     * @param reservedValueSupplier {@link ReservedValueSupplier} for the frame.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public abstract long offer(
        DirectBuffer bufferOne,
        int offsetOne,
        int lengthOne,
        DirectBuffer bufferTwo,
        int offsetTwo,
        int lengthTwo,
        ReservedValueSupplier reservedValueSupplier);

    /**
     * Non-blocking publish by gathering buffer vectors into a message.
     *
     * @param vectors which make up the message.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public final long offer(final DirectBufferVector[] vectors)
    {
        return offer(vectors, null);
    }

    /**
     * Non-blocking publish by gathering buffer vectors into a message.
     *
     * @param vectors               which make up the message.
     * @param reservedValueSupplier {@link ReservedValueSupplier} for the frame.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     */
    public abstract long offer(DirectBufferVector[] vectors, ReservedValueSupplier reservedValueSupplier);

    /**
     * Try to claim a range in the publication log into which a message can be written with zero copy semantics.
     * Once the message has been written then {@link BufferClaim#commit()} should be called thus making it available.
     * A claim length cannot be greater than {@link #maxPayloadLength()}.
     * <p>
     * <b>Note:</b> This method can only be used for message lengths less than MTU length minus header.
     * If the claim is held for more than the aeron.publication.unblock.timeout system property then the driver will
     * assume the publication thread is dead and will unblock the claim thus allowing other threads to make progress
     * for {@link ConcurrentPublication} and other claims to be sent to reach end-of-stream (EOS).
     * <pre>{@code
     *     final BufferClaim bufferClaim = new BufferClaim(); // Can be stored and reused to avoid allocation
     *
     *     if (publication.tryClaim(messageLength, bufferClaim) > 0L)
     *     {
     *         try
     *         {
     *              final MutableDirectBuffer buffer = bufferClaim.buffer();
     *              final int offset = bufferClaim.offset();
     *
     *              // Work with buffer directly or wrap with a flyweight
     *         }
     *         finally
     *         {
     *             bufferClaim.commit();
     *         }
     *     }
     * }</pre>
     *
     * @param length      of the range to claim, in bytes.
     * @param bufferClaim to be populated if the claim succeeds.
     * @return The new stream position, otherwise a negative error value of {@link #NOT_CONNECTED},
     * {@link #BACK_PRESSURED}, {@link #ADMIN_ACTION}, {@link #CLOSED}, or {@link #MAX_POSITION_EXCEEDED}.
     * @throws IllegalArgumentException if the length is greater than {@link #maxPayloadLength()} within an MTU.
     * @see BufferClaim#commit()
     * @see BufferClaim#abort()
     */
    public abstract long tryClaim(int length, BufferClaim bufferClaim);

    /**
     * Add a destination manually to a multi-destination-cast Publication.
     *
     * @param endpointChannel for the destination to add.
     */
    public void addDestination(final String endpointChannel)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        conductor.addDestination(originalRegistrationId, endpointChannel);
    }

    /**
     * Remove a previously added destination manually from a multi-destination-cast Publication.
     *
     * @param endpointChannel for the destination to remove.
     */
    public void removeDestination(final String endpointChannel)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        conductor.removeDestination(originalRegistrationId, endpointChannel);
    }

    /**
     * Remove a previously added destination manually from a multi-destination-cast Publication.
     *
     * @param registrationId for the destination to remove.
     */
    public void removeDestination(final long registrationId)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        conductor.removeDestination(originalRegistrationId, registrationId);
    }

    /**
     * Asynchronously add a destination manually to a multi-destination-cast Publication.
     * <p>
     * Errors will be delivered asynchronously to the {@link Aeron.Context#errorHandler()}. Completion can be
     * tracked by passing the returned correlation id to {@link Aeron#isCommandActive(long)}.
     *
     * @param endpointChannel for the destination to add.
     * @return the correlationId for the command.
     */
    public long asyncAddDestination(final String endpointChannel)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        return conductor.asyncAddDestination(registrationId, endpointChannel);
    }

    /**
     * Asynchronously remove a previously added destination from a multi-destination-cast Publication.
     * <p>
     * Errors will be delivered asynchronously to the {@link Aeron.Context#errorHandler()}. Completion can be
     * tracked by passing the returned correlation id to {@link Aeron#isCommandActive(long)}.
     *
     * @param endpointChannel for the destination to remove.
     * @return the correlationId for the command.
     */
    public long asyncRemoveDestination(final String endpointChannel)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        return conductor.asyncRemoveDestination(registrationId, endpointChannel);
    }

    /**
     * Asynchronously remove a previously added destination from a multi-destination-cast Publication by registrationId.
     * <p>
     * Errors will be delivered asynchronously to the {@link Aeron.Context#errorHandler()}. Completion can be
     * tracked by passing the returned correlation id to {@link Aeron#isCommandActive(long)}.
     *
     * 通过注册ID异步删除多目的地广播发布中先前添加的目的地。
     * <p>
     * 错误将异步传递给{@link Aeron.Context#errorHandler()}。可以通过将返回的相关ID传递给{@link Aeron#isCommandActive(long)}来跟踪完成情况。
     *
     * @param destinationRegistrationId for the destination to remove.
     * @return the correlationId for the command.
     */
    public long asyncRemoveDestination(final long destinationRegistrationId)
    {
        if (isClosed)
        {
            throw new AeronException("Publication is closed");
        }

        return conductor.asyncRemoveDestination(registrationId, destinationRegistrationId);
    }

    // 内部关闭方法，设置isClosed标志为true
    void internalClose()
    {
        isClosed = true;
    }

    // 获取日志缓冲区
    LogBuffers logBuffers()
    {
        return logBuffers;
    }

    // 根据当前流位置和消息长度计算背压状态
    final long backPressureStatus(final long currentPosition, final int messageLength)
    {
        // 检查加上消息长度后是否会超过最大可能位置
        if ((currentPosition + align(messageLength + HEADER_LENGTH, FRAME_ALIGNMENT)) >= maxPossiblePosition)
        {
            return MAX_POSITION_EXCEEDED;  // 返回超出最大位置的状态码
        }

        // 检查日志缓冲区是否连接
        if (LogBufferDescriptor.isConnected(logMetaDataBuffer))
        {
            return BACK_PRESSURED;  // 返回背压状态码
        }

        return NOT_CONNECTED;  // 返回未连接状态码
    }

    // 检查长度是否为正数，如果不是则抛出IllegalArgumentException
    final void checkPositiveLength(final int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("invalid length: " + length);
        }
    }

    // 检查负载长度是否有效（非负且不超过最大负载长度）
    final void checkPayloadLength(final int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("invalid length: " + length);
        }

        if (length > maxPayloadLength)
        {
            throw new IllegalArgumentException(
                "claim exceeds maxPayloadLength of " + maxPayloadLength + ", length=" + length);
        }
    }

    // 检查消息长度是否超过最大消息长度限制
    final void checkMaxMessageLength(final int length)
    {
        if (length > maxMessageLength)
        {
            throw new IllegalArgumentException(
                "message exceeds maxMessageLength of " + maxMessageLength + ", length=" + length);
        }
    }

    // 验证两个长度参数并计算它们的总和，确保不溢出
    static int validateAndComputeLength(final int lengthOne, final int lengthTwo)
    {
        if (lengthOne < 0)
        {
            throw new IllegalArgumentException("lengthOne < 0: " + lengthOne);
        }

        if (lengthTwo < 0)
        {
            throw new IllegalArgumentException("lengthTwo < 0: " + lengthTwo);
        }

        final int totalLength = lengthOne + lengthTwo;
        if (totalLength < 0)
        {
            throw new IllegalArgumentException("overflow totalLength=" + totalLength);
        }

        return totalLength;
    }

    /**
     * Returns a string representation of a position. Generally used for errors. If the position is a valid error then
     * String name of the error will be returned. If the value is 0 or greater the text will be "NONE". If the position
     * is negative, but not a known error code then "UNKNOWN" will be returned.
     *
     * 返回位置的字符串表示形式。通常用于错误。如果位置是有效的错误，则返回错误的字符串名称。如果值为0或更大，则文本将是"NONE"。如果位置为负数，但不是已知错误代码，则返回"UNKNOWN"。
     *
     * @param position position value returned from a call to offer.
     * @return String representation of the error.
     */
    public static String errorString(final long position)
    {
        if (MAX_POSITION_EXCEEDED <= position && position < 0)
        {
            final int errorCode = (int)position;
            switch (errorCode)
            {
                case (int)NOT_CONNECTED:
                    return "NOT_CONNECTED";
                case (int)BACK_PRESSURED:
                    return "BACK_PRESSURED";
                case (int)ADMIN_ACTION:
                    return "ADMIN_ACTION";
                case (int)CLOSED:
                    return "CLOSED";
                case (int)MAX_POSITION_EXCEEDED:
                    return "MAX_POSITION_EXCEEDED";
                default:
                    return "UNKNOWN";
            }
        }
        else if (0 <= position)
        {
            return "NONE";
        }
        else
        {
            return "UNKNOWN";
        }
    }

    /**
     * {@inheritDoc}
     * 
     * 返回此Publication对象的字符串表示形式，包含关键属性信息
     */
    public String toString()
    {
        return "Publication{" +
            "originalRegistrationId=" + originalRegistrationId +
            ", registrationId=" + registrationId +
            ", isClosed=" + isClosed +
            ", isConnected=" + isConnected() +
            ", initialTermId=" + initialTermId +
            ", termBufferLength=" + termBufferLength +
            ", sessionId=" + sessionId +
            ", streamId=" + streamId +
            ", channel='" + channel + '\'' +
            ", position=" + position() +
            '}';
    }
}