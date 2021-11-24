package constant;

public class ConstantValue {

    // =====================================共用的值=====================================

    public static final int NODE_SIZE = 4;
    public static final int TRANSACTION_NUMBER = 1;
//    public static final int TRANSACTION_SIZE = 1 * 1024 * 1024 * 8; // 1M
    // 每个节点的带宽固定, 增加节点的同时会增加整个网络的带宽, 这样的耗时是一次曲线
    public static final int BANDWIDTH = 100 * 1024;
    // 限制所有节点的带宽, 然后均分给所有节点, 这样的耗时是二次曲线
//    public static final int TOTAL_BANDWIDTH = 1024 * 1024;
//    public static final int BANDWIDTH = TOTAL_BANDWIDTH / NODE_SIZE;
    public static final int MSG_TYPE_ID_SIZE = 4; // 用于标识消息类型, 类型不多于16种, 可以标识完全
    public static final int TIMESTAMP_SIZE = 64; //时间戳为long类型, 64位
    public static final int ID_SIZE = 16; // 单位: b
    public static final int HASH_SIZE = 256;
    public static final int SIGNATURE_SIZE = 512;
    public static final int RESULT_SIZE = 1; // 值为0或1, 用于reply中的r和bilayer消息中的b
    public static final long TO_ITSELF_DELAY = 10;

    // =====================================PBFT=====================================

    public static final int VIEW_NO_SIZE = 8;
    public static final int SEQ_NO_SIZE = 16;
    public static final int C_SET_SIZE = 10000; // 表示集合的总共大小 (估计)
    public static final int P_SET_SIZE = 10000;
    public static final long INIT_TIMEOUT = 5000; // 单位: ms
    public static final long FAST_NET_DELAY = 10;
    public static final long SLOW_NET_DELAY = 60;

    // =====================================bilayer=====================================

    public static final int PK_SIZE = 512;
    public static final long SEND_WEIGHT_TIME = 10000; // 根据实际情况设置
    public static final long SEND_NO_BLOCK_TIME = 20000; // 根据实际情况设置
    public static final long GROUP_INSIDE_FAST_NET_DELAY = 10;
    public static final long GROUP_INSIDE_SLOW_NET_DELAY = 30;
    public static final long GROUP_OUTSIDE_FAST_NET_DELAY = 10;
    public static final long GROUP_OUTSIDE_SLOW_NET_DELAY = 60;

}
