package bilayer;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static constant.ConstantValue.*;

public class bilayerBFTNode {

    Logger logger = LoggerFactory.getLogger(getClass());

    private int n;                                                  // 总节点数
    private int maxF;                                               // 最大容错数, 对应论文中的f
    private int index;                                              // 该节点的标识
    private bilayerBFTMsg curREQMsg;                                // 当前正在处理的请求
    private long SendWeightTime = SEND_WEIGHT_TIME;                 // 隔多久发送WEIGHT消息, 根据实际情况调整
    private long SendNoBlockTime = SEND_NO_BLOCK_TIME;              // 隔多久发送NO_BLOCK消息, 根据实际情况调整
    public double totalSendMsgLen = 0;                              // 发送的所有消息的长度之和
    private volatile boolean isRunning = false;                     // 是否正在运行, 可用于设置Crash节点

    private int groupSize;                                          // 组大小
    private boolean isLeader;                                       // 是否是leader
    private int weight;                                             // 用于是leader时的权重记录


    // 消息队列
    private BlockingQueue<bilayerBFTMsg> qbm = Queues.newLinkedBlockingQueue();

    // RBC解码后区块记录
    private Set<String> RBCMsgRecord = Sets.newConcurrentHashSet();

    // 准备阶段消息记录
    private Set<String> PAMsgRecord = Sets.newConcurrentHashSet();
    // 记录已经收到的PA消息对应的数量
    private AtomicLongMap<String> PAMsgCountMap = AtomicLongMap.create();

    // 提交阶段消息记录
    private Set<String> CMMsgRecord = Sets.newConcurrentHashSet();
    // 记录已经收到的CM消息对应的数量
    private AtomicLongMap<String> CMMsgCountMap = AtomicLongMap.create();

    // 回复消息数量
    private AtomicLong replyMsgCount = new AtomicLong();

    // 已经成功处理过的请求
    private Map<String,bilayerBFTMsg> doneMsgRecord = Maps.newConcurrentMap();

    // 存入client利用RBC发出区块的时间, 用于判断何时发送WEIGHT和NO_BLOCK消息
    private Map<String,Long> RBCStartTime = Maps.newHashMap();

    // 请求队列
    private BlockingQueue<bilayerBFTMsg> reqQueue = Queues.newLinkedBlockingDeque();

    // 权重累加值
    private AtomicLong WeightSum = new AtomicLong();

    private Timer timer;

    public bilayerBFTNode(int index, int n, boolean isLeader) {
        this.index = index;
        this.n = n;
        this.maxF = (n-1) / 3;
        this.isLeader = isLeader;
        timer = new Timer("Timer"+index);
    }



//    // 广播消息
//    public synchronized void publish(PBFTMsg msg){
//        logger.info("[节点" + msg.getSenderId() + "]广播消息:" + msg);
//        for(int i = 0; i < PBFTMain.size; i++) {
//            send(i, new PBFTMsg(msg)); // 广播时发送消息的复制
//        }
//    }
//
//    // 发送消息给指定节点, 加上synchronized按顺序发送
//    public synchronized void send(int toIndex, PBFTMsg msg) {
//        // 模拟发送时长
//        try {
//            Thread.sleep(sendMsgTime(msg, BANDWIDTH));
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        totalSendMsgLen += msg.getMsgLen();
//
//        // 模拟网络时延
//        TimerManager.schedule(()-> {
//            PBFTMain.nodes[toIndex].pushMsg(msg);
//            return null;
//        }, bilayerBFTMain.netDelay[msg.getSenderId()][toIndex]);
//    }
//
//    // 发送消息所耗的时长, 单位ms
//    public long sendMsgTime(PBFTMsg msg, int bandwidth) {
//        return msg.getMsgLen() * 1000 / bandwidth;
//    }
//
//    public void pushMsg(PBFTMsg msg){
//        try {
//            this.qbm.put(msg);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }



    // 为了方便, 将节点序号隔OFFSET个分为一组, 第一个能被OFFSET整除的序号对应的是leader
    public int getLeaderNode(int index) {
        return index / OFFSET * OFFSET;
    }

    public void NodeCrash(){
        logger.info("[节点" + index + "]宕机--------------");
        this.isRunning = false;
    }

    public void NodeRecover() {
        logger.info("[节点" + index + "]恢复--------------");
        this.isRunning = true;
    }

}
