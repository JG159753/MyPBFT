package constant;

public class ConstantValue {

    public static final int NODE_SIZE = 100;

    public static final int TRANSACTION_NUMBER = 1;

    public static final int BANDWIDTH = 100 * 1024;

    public static final int MSG_TYPE_ID_SIZE = 4; // 用于标识消息类型, 类型不多于16种, 可以标识完全

    public static final int TIMESTAMP_SIZE = 64; //时间戳为long类型, 64位

    public static final int ID_SIZE = 16; // 单位: b

    public static final int HASH_SIZE = 256;

    public static final int VIEW_NO_SIZE = 8;

    public static final int SEQ_NO_SIZE = 16;

    public static final int RESULT_SIZE = 1; // reply消息中的r, 这边就只用0和1表示是否成功

    public static final int C_SET_SIZE = 10000; // 表示集合的总共大小 (估计)

    public static final int P_SET_SIZE = 10000;

    public static final int PK_SIZE = 512;

    public static final int SIGNATURE_SIZE = 512;

    public static final long INIT_TIMEOUT = 5000; // 单位: ms

    public static final long FAST_NET_DELAY = 10;

    public static final long SLOW_NET_DELAY = 60;

    public static final long TO_ITSELF_DELAY = 10;

}
