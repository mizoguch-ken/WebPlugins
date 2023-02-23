/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.onlineart;

import java.io.UnsupportedEncodingException;

/**
 *
 * @author mizoguch-ken
 */
public class OnlineArtSendPacket extends OnlineArtPacket {

    private long sentTimeNanos_;
    private int[] calculationBuffer_;
    private int[] sendBuffer_;
    private int sendIndex_;
    private boolean isSendRequest_;
    private boolean isSendRequestTrigger_;
    private int maxRegisterNumber_;
    private boolean isDivided_;
    private int dividedStartAddress_;
    private int dividedAddressOffset_;
    private int dividedRegisterNumber_;

    /**
     *
     */
    public OnlineArtSendPacket() {
        sentTimeNanos_ = 0;
        calculationBuffer_ = new int[MAX_BUFFER_LENGTH];
        sendBuffer_ = new int[MAX_BUFFER_LENGTH];
        sendIndex_ = 0;
        isSendRequest_ = false;
        isSendRequestTrigger_ = false;
        maxRegisterNumber_ = 0;
        isDivided_ = false;
        dividedStartAddress_ = 0;
        dividedAddressOffset_ = 0;
        dividedRegisterNumber_ = 0;
    }

    @Override
    public void close() {
        super.close();
        calculationBuffer_ = null;
        sendBuffer_ = null;
    }

    /**
     *
     * @return
     */
    public long getSentTimeNanos() {
        return sentTimeNanos_;
    }

    /**
     *
     * @param sentTimeNanos
     */
    public void setSentTimeNanos(long sentTimeNanos) {
        sentTimeNanos_ = sentTimeNanos;
    }

    /**
     *
     * @return
     */
    public int[] getBuffer() {
        return sendBuffer_;
    }

    /**
     *
     * @param position
     * @return
     */
    public int getBuffer(int position) {
        if ((0 <= position) && (position < sendBuffer_.length)) {
            return (sendBuffer_[position] & 0xff);
        }
        return -1;
    }

    /**
     *
     * @return
     */
    public int getIndex() {
        return sendIndex_;
    }

    /**
     *
     * @return
     */
    public boolean isSendRequest() {
        return isSendRequest_;
    }

    /**
     *
     * @param sendRequest
     */
    public void setSendRequest(boolean sendRequest) {
        isSendRequest_ = sendRequest;
    }

    /**
     *
     * @return
     */
    public boolean isSendRequestTrigger() {
        return isSendRequestTrigger_;
    }

    /**
     *
     * @param sendRequestTrigger
     */
    public void setSendRequestTrigger(boolean sendRequestTrigger) {
        isSendRequestTrigger_ = sendRequestTrigger;
    }

    /**
     *
     * @return
     */
    public int getMaxRegisterNumber() {
        return maxRegisterNumber_;
    }

    /**
     *
     * @param maxRegisterNumber
     */
    public void setMaxRegisterNumber(int maxRegisterNumber) {
        maxRegisterNumber_ = maxRegisterNumber;
    }

    /**
     *
     * @return
     */
    public boolean isDivided() {
        return isDivided_;
    }

    /**
     *
     * @param source
     * @param maxUnitNumber
     * @param baseTime
     * @param delayTime
     * @param retryNumber
     * @param maxRegisterNumber
     * @param connectionUnits
     */
    public void makeSynchronizeRequest(int source, int maxUnitNumber, int baseTime, int delayTime, int retryNumber, int maxRegisterNumber, int connectionUnits) {
        isDivided_ = false;

        setDestination(DESTINATION_ALL);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_SYNCHRONIZE);

        makeHeader();
        sendIndex_ += setBuffer(maxUnitNumber, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(baseTime, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(delayTime, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(retryNumber, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(maxRegisterNumber, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(connectionUnits, sendBuffer_, sendIndex_);
        makeFooter();
    }

    /**
     *
     * @param source
     */
    public void makeStatusRequest(int source) {
        isDivided_ = false;

        setDestination(DESTINATION_ALL);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_STATUS);

        makeHeader();
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     */
    public void makeReadRelayRequest(int destination, int source, int startAddress, int addressOffset, int registerNumber) {
        isDivided_ = false;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_READ_RELAY);

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(registerNumber, sendBuffer_, sendIndex_);
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     */
    public void makeReadRegisterRequest(int destination, int source, int startAddress, int addressOffset, int registerNumber) {
        isDivided_ = false;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_READ_REGISTER);

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(registerNumber, sendBuffer_, sendIndex_);
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     * @param relay
     */
    public void makeWriteRelayRequest(int destination, int source, int startAddress, int addressOffset, int registerNumber, int[] relay) {
        int index, data, count, i;

        isDivided_ = false;
        index = 0;
        data = 0;
        count = 0;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_WRITE_RELAY);

        for (i = 0; i < registerNumber; i++) {
            if (((relay[(i + startAddress) / 16] >>> ((i + startAddress) % 16)) & 0x0001) == 0x0001) {
                data |= 0x0001 << (i % 16);
            } else {
                data &= (0x0001 << (i % 16)) ^ 0xffff;
            }
            count++;
            if (((i % 16) == 15) || (i >= (registerNumber - 1))) {
                index += setBuffer(data, calculationBuffer_, index);
                if ((maxRegisterNumber_ * 2) <= index) {
                    isDivided_ = true;
                    dividedStartAddress_ = startAddress + count;
                    dividedAddressOffset_ = addressOffset;
                    dividedRegisterNumber_ = registerNumber - count;
                    break;
                }
                data = 0;
            }
        }

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(count, sendBuffer_, sendIndex_);
        System.arraycopy(calculationBuffer_, 0, sendBuffer_, sendIndex_, index);
        sendIndex_ += index;
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     * @param register
     */
    public void makeWriteRegisterRequest(int destination, int source, int startAddress, int addressOffset, int registerNumber, int[] register) {
        int index, count, i;

        isDivided_ = false;
        index = 0;
        count = 0;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_REQUEST);
        setCommand(COMMAND_WRITE_REGISTER);

        for (i = 0; i < registerNumber; i++) {
            index += setBuffer(register[i + startAddress], calculationBuffer_, index);
            count++;
            if ((maxRegisterNumber_ * 2) <= index) {
                isDivided_ = true;
                dividedStartAddress_ = startAddress + count;
                dividedAddressOffset_ = addressOffset;
                dividedRegisterNumber_ = registerNumber - count;
                break;
            }
        }

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(count, sendBuffer_, sendIndex_);
        System.arraycopy(calculationBuffer_, 0, sendBuffer_, sendIndex_, index);
        sendIndex_ += index;
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     * @param relay
     */
    public void makeReadRelayResponse(int destination, int source, int startAddress, int addressOffset, int registerNumber, int[] relay) {
        int index, data, count, i;

        isDivided_ = false;
        index = 0;
        data = 0;
        count = 0;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_RESPONSE);
        setCommand(COMMAND_READ_RELAY);

        for (i = 0; i < registerNumber; i++) {
            if (((relay[(i + startAddress) / 16] >>> ((i + startAddress) % 16)) & 0x0001) == 0x0001) {
                data |= 0x0001 << (i % 16);
            } else {
                data &= (0x0001 << (i % 16)) ^ 0xffff;
            }
            count++;
            if (((i % 16) == 15) || (i >= (registerNumber - 1))) {
                index += setBuffer(data, calculationBuffer_, index);
                if ((maxRegisterNumber_ * 2) <= index) {
                    isDivided_ = true;
                    dividedStartAddress_ = startAddress + count;
                    dividedAddressOffset_ = addressOffset;
                    dividedRegisterNumber_ = registerNumber - count;
                    break;
                }
                data = 0;
            }
        }

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(count, sendBuffer_, sendIndex_);
        System.arraycopy(calculationBuffer_, 0, sendBuffer_, sendIndex_, index);
        sendIndex_ += index;
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     * @param register
     */
    public void makeReadRegisterResponse(int destination, int source, int startAddress, int addressOffset, int registerNumber, int[] register) {
        int index, count, i;

        isDivided_ = false;
        index = 0;
        count = 0;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_RESPONSE);
        setCommand(COMMAND_READ_REGISTER);

        for (i = 0; i < registerNumber; i++) {
            index += setBuffer(register[i + startAddress], calculationBuffer_, index);
            count++;
            if ((maxRegisterNumber_ * 2) <= index) {
                isDivided_ = true;
                dividedStartAddress_ = startAddress + count;
                dividedAddressOffset_ = addressOffset;
                dividedRegisterNumber_ = registerNumber - count;
                break;
            }
        }

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(count, sendBuffer_, sendIndex_);
        System.arraycopy(calculationBuffer_, 0, sendBuffer_, sendIndex_, index);
        sendIndex_ += index;
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     */
    public void makeWriteRelayResponse(int destination, int source, int startAddress, int addressOffset, int registerNumber) {
        isDivided_ = false;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_RESPONSE);
        setCommand(COMMAND_WRITE_RELAY);

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(registerNumber, sendBuffer_, sendIndex_);
        makeFooter();
    }

    /**
     *
     * @param destination
     * @param source
     * @param startAddress
     * @param addressOffset
     * @param registerNumber
     */
    public void makeWriteRegisterResponse(int destination, int source, int startAddress, int addressOffset, int registerNumber) {
        isDivided_ = false;

        setDestination(destination);
        setSource(source);
        setDataType(DATATYPE_RESPONSE);
        setCommand(COMMAND_WRITE_REGISTER);

        makeHeader();
        sendIndex_ += setBuffer(startAddress + addressOffset, sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(registerNumber, sendBuffer_, sendIndex_);
        makeFooter();
    }

    /**
     *
     * @param relay
     * @param register
     */
    public void makeDivided(int[] relay, int[] register) {
        if (isDivided_) {
            if (getDataType() == DATATYPE_REQUEST) {
                switch (getCommand()) {
                    case COMMAND_READ_RELAY:
                        makeReadRelayRequest(getDestination(), getSource(), dividedStartAddress_, dividedAddressOffset_, dividedRegisterNumber_);
                        break;
                    case COMMAND_READ_REGISTER:
                        makeReadRegisterRequest(getDestination(), getSource(), dividedStartAddress_, dividedAddressOffset_, dividedRegisterNumber_);
                        break;
                    case COMMAND_WRITE_RELAY:
                        makeWriteRelayRequest(getDestination(), getSource(), dividedStartAddress_, dividedAddressOffset_, dividedRegisterNumber_, relay);
                        break;
                    case COMMAND_WRITE_REGISTER:
                        makeWriteRegisterRequest(getDestination(), getSource(), dividedStartAddress_, dividedAddressOffset_, dividedRegisterNumber_, register);
                        break;
                    case COMMAND_STATUS:
                        makeStatusRequest(getSource());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void makeHeader() {
        sendIndex_ = 0;
        sendBuffer_[sendIndex_++] = START_CODE_STX;
        sendIndex_ += setBuffer(getDestination(), getSource(), sendBuffer_, sendIndex_);
        sendIndex_ += setBuffer(getDataType(), getCommand(), sendBuffer_, sendIndex_);
    }

    private void makeFooter() {
        if ((sendIndex_ + 6) < sendBuffer_.length) {
            int calcCrc, i;

            calcCrc = 0;
            for (i = 1; i < sendIndex_; i++) {
                calcCrc = (CRC16_TABLE[((calcCrc >>> 8) ^ sendBuffer_[i]) & 0xff] ^ (calcCrc << 8)) & 0xffff;
            }
            try {
                for (byte b : String.format("%04X", calcCrc).getBytes("UTF-8")) {
                    sendBuffer_[sendIndex_++] = b;
                }
            } catch (UnsupportedEncodingException ex) {
            }
            sendBuffer_[sendIndex_++] = END_CODE_CR;
            sendBuffer_[sendIndex_++] = END_CODE_LF;
        } else {
            sendIndex_ = 0;
        }
    }

    private int setBuffer(int msb, int lsb, int[] buffer, int index) {
        if (0 <= index) {
            if (((msb == ESCAPE_CODE_7D) && (lsb == ESCAPE_CODE_7D)) || ((msb == END_CODE_CR) && (lsb == END_CODE_LF)) || ((index > 0) && ((buffer[index - 1] == END_CODE_CR) && (msb == END_CODE_LF)))) {
                if ((index + 3) < buffer.length) {
                    buffer[index] = ESCAPE_CODE_7D;
                    buffer[index + 1] = ESCAPE_CODE_7D;
                    buffer[index + 2] = msb ^ XOR_CODE_20;
                    buffer[index + 3] = lsb ^ XOR_CODE_20;
                    return 4;
                }
            } else if ((index + 1) < buffer.length) {
                buffer[index] = msb;
                buffer[index + 1] = lsb;
                return 2;
            }
        }
        return -1;
    }

    private int setBuffer(int data, int[] buffer, int index) {
        if (0 <= index) {
            if ((data == ESCAPE_CODE) || (data == END_CODE) || ((index > 0) && ((buffer[index - 1] == END_CODE_CR) && (((data >>> 8) & 0xff) == END_CODE_LF)))) {
                if ((index + 3) < buffer.length) {
                    buffer[index] = ESCAPE_CODE_7D;
                    buffer[index + 1] = ESCAPE_CODE_7D;
                    buffer[index + 2] = ((data >>> 8) & 0xff) ^ XOR_CODE_20;
                    buffer[index + 3] = (data & 0xff) ^ XOR_CODE_20;
                    return 4;
                }
            } else if ((index + 1) < buffer.length) {
                buffer[index] = (data >>> 8) & 0xff;
                buffer[index + 1] = data & 0xff;
                return 2;
            }
        }
        return -1;
    }
}
