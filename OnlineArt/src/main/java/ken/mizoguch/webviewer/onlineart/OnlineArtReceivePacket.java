/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.onlineart;

/**
 *
 * @author mizoguch-ken
 */
public class OnlineArtReceivePacket extends OnlineArtPacket {

    private int[] buffer_;
    private int index_;
    private int[] data_;

    /**
     *
     */
    public OnlineArtReceivePacket() {
        buffer_ = new int[MAX_BUFFER_LENGTH];
        index_ = 0;
        data_ = new int[MAX_DATA_LENGTH];
    }

    /**
     *
     */
    @Override
    public void close() {
        super.close();
        buffer_ = null;
        data_ = null;
    }

    /**
     *
     * @return
     */
    public int[] getBuffer() {
        return buffer_;
    }

    /**
     *
     * @return
     */
    public int getIndex() {
        return index_;
    }

    /**
     *
     * @return
     */
    public int[] getData() {
        return data_;
    }

    /**
     *
     * @param position
     * @return
     */
    public int getData(int position) {
        if ((0 <= position) && (position < data_.length)) {
            return data_[position];
        }
        return -1;
    }

    /**
     *
     * @param data
     * @return
     */
    public boolean read(int data) {
        boolean isEscape;
        int dataCrc, calculationCrc, count, i;

        buffer_[index_++] = data;
        if (index_ >= 2) {
            // CRLF
            if ((buffer_[index_ - 2] == END_CODE_CR) && (buffer_[index_ - 1] == END_CODE_LF)) {
                // STX
                if ((index_ >= 6) && ((index_ % 2) == 1) && (buffer_[0] == START_CODE_STX)) {
                    dataCrc = 0;
                    for (i = (index_ - 6); i < (index_ - 2); i++) {
                        if ((0x30 <= buffer_[i]) && (buffer_[i] <= 0x39)) {
                            dataCrc |= (buffer_[i] - 0x30) << ((index_ - i - 3) * 4);
                        } else if ((0x41 <= buffer_[i]) && (buffer_[i] <= 0x46)) {
                            dataCrc |= (buffer_[i] - 0x37) << ((index_ - i - 3) * 4);
                        } else {
                            index_ = 0;
                            return false;
                        }
                    }

                    isEscape = false;
                    calculationCrc = 0;
                    count = 0;
                    for (i = 1; i < (index_ - 6); i += 2) {
                        calculationCrc = (CRC16_TABLE[((calculationCrc >>> 8) ^ buffer_[i]) & 0xff] ^ (calculationCrc << 8)) & 0xffff;
                        calculationCrc = (CRC16_TABLE[((calculationCrc >>> 8) ^ buffer_[i + 1]) & 0xff] ^ (calculationCrc << 8)) & 0xffff;
                        if ((buffer_[i] == ESCAPE_CODE_7D) && (buffer_[i + 1] == ESCAPE_CODE_7D)) {
                            isEscape = true;
                        } else {
                            if (isEscape) {
                                isEscape = false;
                                switch (count) {
                                    case 0:
                                        setDestination(buffer_[i] ^ XOR_CODE_20);
                                        setSource(buffer_[i + 1] ^ XOR_CODE_20);
                                        break;
                                    case 1:
                                        setDataType(buffer_[i] ^ XOR_CODE_20);
                                        setCommand(buffer_[i + 1] ^ XOR_CODE_20);
                                        break;
                                    default:
                                        if ((count - 2) >= data_.length) {
                                            index_ = 0;
                                            return false;
                                        }
                                        data_[count - 2] = ((buffer_[i] ^ XOR_CODE_20) << 8) | (buffer_[i + 1] ^ XOR_CODE_20);
                                        break;
                                }
                            } else {
                                switch (count) {
                                    case 0:
                                        setDestination(buffer_[i]);
                                        setSource(buffer_[i + 1]);
                                        break;
                                    case 1:
                                        setDataType(buffer_[i]);
                                        setCommand(buffer_[i + 1]);
                                        break;
                                    default:
                                        if ((count - 2) >= data_.length) {
                                            index_ = 0;
                                            return false;
                                        }
                                        data_[count - 2] = (buffer_[i] << 8) | buffer_[i + 1];
                                        break;
                                }
                            }
                            count++;
                        }
                    }

                    if (dataCrc == calculationCrc) {
                        index_ = 0;
                        return true;
                    }
                }
                index_ = 0;
                return false;
            }
        }
        if (index_ >= buffer_.length) {
            index_ = 0;
        }
        return false;
    }
}
