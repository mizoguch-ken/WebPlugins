/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

/**
 *
 * @author mizoguch-ken
 */
public enum CapAiSystemEnums {
    LINK_BOX_CLIENT("LinkBoxClient"),
    LINK_BOX_SERVER("LinkBoxServer"),
    DEVICE_REGISTRATION_EXECUTION("Device Registration Execution", "接続機器登録実行中"),
    NO_0_ADDRESSING("No. 0 addressing", "0番アドレス指定"),
    WILD_CARD_ADDRESSING("Wild card addressing", "ワイルドカードアドレス指定"),
    SPECIFY_LOCKED_EQUIPMENT("Specify Locked Equipment", "ロックされた機器を指定"),
    SPECIFY_PART_LOCKED_EQUIPMENT("Specify Part Locked Equipment (lock, unlock, if it already exists in part equipment that is locked)", "一部ロックされた機器を指定(ロック、アンロックで、既にロックされている機器が一部存在する場合)"),
    DISCONNECTED("Disconnected", "未接続"),
    OVERLAPPING_CONNECTION("Overlapping Connection", "重複接続"),
    OVER_CONNECTIONS("Over Connections", "接続数オーバー"),
    ABNORMAL_EQUIPMENT_ADDRESS("Abnormal Equipment Address(> 9999)", "機器アドレス異常(>9999)"),
    SPECIFY_DURING_MAINTENANCE_EQUIPMENT("Specify During Maintenance Equipment", "メンテナンス中の機器を指定"),
    SPECIFY_WORK_ORDERS_EQUIPMENT("Specify Work Orders Equipment", "作業指示中の機器を指定"),
    SPECIFY_OPEN_EQUIPMENT("Specify Open Equipment", "オープン中の機器を指定"),
    ABNORMAL_POLLING_RESPONSE_ADDRESS("Abnormal Polling Response Address", "ポーリングレスポンスアドレス異常"),
    ABNORMAL_BARCODE_READER_INTERFACE("Abnormal Barcode Reader Interface", "バーコードリーダインターフェイス異常"),
    STATUS_ERROR("Status Error", "ステータス異常"),
    ABNORMAL_ADDRESSING("Abnormal Addressing", "アドレス指定異常"),
    LOCAL_COMMUNICATION_RECEIVE_TIMEOUT("Local Communication: Receive Time-out (No response)", "ローカル通信 受信タイムアウト(無応答)"),
    LOCAL_COMMUNICATION_ERROR_INTERRUPT_OCCURS("Local Communication: Error Interrupt Occurs (Framing, Overrun error)", "ローカル通信 エラー割り込み発生(フレーミング、オーバーランエラー)"),
    LOCAL_COMMUNICATION_ABNORMAL_BCC("Local Communication: Abnormal BCC", "ローカル通信 BCC異常"),
    LOCAL_COMMUNICATION_ABNORMAL_RECEPTION_DENBUN_FORMAT("Local Communication: Abnormal Reception Denbun Format", "ローカル通信 受信伝文フォーマット異常"),
    LOCAL_COMMUNICATION_ANN_CIRCUIT_STATUS_ERROR("Local Communication: Ann Circuit Etatus Error", "ローカル通信 アンサーキットステータス異常"),
    HOST_COMMUNICATION_NO_FREE_CONNECTION_PORT("Host Communication: No Free Connection Port", "ホスト通信 接続ポートに空きがない"),
    HOST_COMMUNICATION_DISABLE_COMMAND("Host Communication: Disable Command", "ホスト通信 無効コマンド"),
    HOST_COMMUNICATION_COMMAND_PARAMETER_ERROR("Host Communication: Command Parameter Error", "ホスト通信 コマンドパラメータ異常"),
    HOST_COMMUNICATION_INVALID_HOST_IP("Host Communication: Invalid Host IP", "ホスト通信 無効なホスト IP"),
    HOST_COMMAND_NOT_EXECUTABLE("Host Command Not Executable (During polling)", "ホストコマンド実行不可(ポーリング中)"),
    HOST_COMMUNICATION_AKATT_NOT_ACCEPTED("Host Communication: AKATT Not Accepted", "ホスト通信 AKATT 未受理"),
    HOST_COMMUNICATION_SOCKET_CONNECTION_ERROR("Host Communication: Socket Connection Error (50021)", "ホスト通信 ソケット接続エラー(50021)"),
    LOCAL_COMMUNICATION_CH1_OVERCURRENT("Local Communication: ch1 Overcurrent", "ローカル通信 ch1 過電流"),
    LOCAL_COMMUNICATION_CH2_OVERCURRENT("Local Communication: ch2 Overcurrent", "ローカル通信 ch2 過電流"),
    LOCAL_COMMUNICATION_CH3_OVERCURRENT("Local Communication: ch3 Overcurrent", "ローカル通信 ch3 過電流"),
    LOCAL_COMMUNICATION_CH4_OVERCURRENT("Local Communication: ch4 Overcurrent", "ローカル通信 ch4 過電流"),
    LOCAL_COMMUNICATION_CH5_OVERCURRENT("Local Communication: ch5 Overcurrent", "ローカル通信 ch5 過電流"),
    LOCAL_COMMUNICATION_CH6_OVERCURRENT("Local Communication: ch6 Overcurrent", "ローカル通信 ch6 過電流"),
    OPERATION_AUTOMATIC_COMMUNICATION_MODE("Operation Automatic Communication Mode", "自動通信モード作動中"),
    DUPLICATION_CONTROL_1("Duplication Control 1", "重複制御1"),
    DUPLICATION_CONTROL_2("Duplication Control 2", "重複制御2"),
    INVALID_CONTROL_1("Invalid Control 1", "制御無効1"),
    INVALID_CONTROL_2("Invalid Control 2(The command received during sleep)", "制御無効2(スリープ中にコマンド受信)"),
    HOST_COMMUNICATION_FORCED_DISCONNECTION("Host Communication: Forced Disconnection", "ホスト通信 強制切断された"),
    SET_ADDRESS("Please set the address", "アドレスを設定してください"),
    FAILED_WORK_ORDER_PORT_ACQUISITION("Failed Work Order Port Acquisition", "作業指示用のポート取得に失敗しました"),
    NO_RESPONSE("No Response", "レスポンスがありません"),
    ABNORMAL_REQUEST_RECEIVING("Abnormal Request Receiving", "リクエスト送受信異常"),
    REQUEST_SENDING_RECEIVING_DATA_ERROR("Request Sending And Receiving Data Error", "リクエスト送受信データ異常"),
    REQUEST("Request", "リクエスト"),
    RESPONSE("Response", "レスポンス");

    private int lang_ = 0;
    private final String[] text_ = new String[2];

    private CapAiSystemEnums(final String message) {
        for (int i = 0; i < text_.length; i++) {
            text_[i] = message;
        }
    }

    private CapAiSystemEnums(final String en, final String ja) {
        text_[0] = en;
        text_[1] = ja;
    }

    public void setLang(int l) {
        lang_ = l;
    }

    @Override
    public String toString() {
        return text_[lang_];
    }
}
