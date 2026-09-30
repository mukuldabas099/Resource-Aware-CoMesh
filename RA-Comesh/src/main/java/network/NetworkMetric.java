package network;

import network.message.MessageType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NetworkMetric {

    private static class MessageCount {
        private final HashMap<MessageType, Integer> _counts = new HashMap<>();

        public MessageCount() {
        }

        public MessageCount(MessageCount messageCount) {
            for (Map.Entry<MessageType, Integer> entry : messageCount._counts.entrySet()) {
                _counts.put(entry.getKey(), entry.getValue());
            }
        }

        public MessageCount minus(MessageCount other) {
            MessageCount res = new MessageCount();
            for (MessageType type : _counts.keySet()) {
                res.set(type, this._counts.get(type) - other.get(type));
            }
            return res;
        }

        /** Records one message/ */
        public void record(MessageType type) {
            _counts.put(type, _counts.getOrDefault(type, 0) + 1);
        }

        /** Gets message number for one MessageType. */
        public int get(MessageType type) {
            return _counts.getOrDefault(type, 0);
        }

        /** Sets the message number of one MessageType to a specific value. */
        public void set(MessageType type, int val) {
            _counts.put(type, val);
        }

        /**
         * Gets message number for a specific type.
         * Currently supports: background and foreground.
         */
        private List<MessageType> getTargetTypes(String target) {
            List<MessageType> target_types = new ArrayList<>();
            if (target.equals("background")) {
                for (MessageType type : MessageType.values()) {
                    String tname = type.name();
                    if (tname.startsWith("DEVICE_STATE") ||
                            tname.startsWith("DEVICE_READING") ||
                            tname.startsWith("TRIGGER") ||
                            tname.startsWith("LOCK") ||
                            tname.endsWith("INFO")) {
                        continue;
                    }
                    target_types.add(type);
                }
            } else { // foreground types
                for (MessageType type : MessageType.values()) {
                    String tname = type.name();
                    if (tname.startsWith("DEVICE_STATE") ||
                            tname.startsWith("DEVICE_READING") ||
                            tname.startsWith("TRIGGER") ||
                            tname.startsWith("LOCK") ||
                            tname.endsWith("INFO")) {
                        target_types.add(type);
                    }
                }
            }
            return target_types;
        }

        public Map<MessageType, Integer> getCountByType(List<MessageType> types) {
            return types.stream()
                    .filter(_counts::containsKey)
                    .collect(Collectors.toMap(Function.identity(), _counts::get));
        }

        public int getBackgroundMsgCount() {
            if (_counts.isEmpty()) {
                return 0;
            }
            List<MessageType> BG_MSG_TYPE = getTargetTypes("background");
            Map<MessageType, Integer> counts = getCountByType(BG_MSG_TYPE);
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getForegroundMsgCount() {
            if (_counts.isEmpty()) {
                return 0;
            }
            List<MessageType> FG_MSG_TYPE = getTargetTypes("foreground");
            Map<MessageType, Integer> counts = getCountByType(FG_MSG_TYPE);
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getAllMsgCount() {
            if (_counts.isEmpty()) {
                return 0;
            }
            return _counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private HashMap<String, HashMap<String, MessageCount>> _hop2hop_msgs = new HashMap<>();
    private HashMap<String, HashMap<String, MessageCount>> _end2end_msgs = new HashMap<>();
    private HashMap<String, HashMap<String, MessageCount>> _pre_hop2hop_msgs = new HashMap<>();
    private HashMap<String, HashMap<String, MessageCount>> _pre_end2end_msgs = new HashMap<>();

    private int checkpoint_start_time = 0;

    public NetworkMetric() {
    }

    private void recordMsg(String src,
            String dst,
            MessageType type,
            HashMap<String, HashMap<String, MessageCount>> msgs_record) {
        if (!msgs_record.containsKey(src)) {
            msgs_record.put(src, new HashMap<>());
        }
        if (!msgs_record.get(src).containsKey(dst)) {
            msgs_record.get(src).put(dst, new MessageCount());
        }
        msgs_record.get(src).get(dst).record(type);
    }

    public void recordE2EMsg(String src, String dst, MessageType type) {
        recordMsg(src, dst, type, _end2end_msgs);
    }

    public void recordH2HMsg(String src, String dst, MessageType type) {
        recordMsg(src, dst, type, _hop2hop_msgs);
    }

    public void recordRawMsgData(String fname) {
        recordRawMsgData(fname, _end2end_msgs, _hop2hop_msgs);
    }

    private void recordRawMsgData(String fname,
            HashMap<String, HashMap<String, MessageCount>> e2e_msgs,
            HashMap<String, HashMap<String, MessageCount>> h2h_msgs) {
        try {
            File fout = new File(fname);
            if (!fout.exists()) {
                fout.createNewFile();
            }
            FileWriter writer = new FileWriter(fout);
            writer.write("Src,Dst,E2EMsg,E2E_BG,E2E_FG,H2HMsg,H2H_BG,H2H_FG\n");
            writer.close();
            writer = new FileWriter(fout, true);
            for (Map.Entry<String, HashMap<String, MessageCount>> entry : e2e_msgs.entrySet()) {
                String src = entry.getKey();
                for (String dst : entry.getValue().keySet()) {
                    MessageCount h2h_count = h2h_msgs.getOrDefault(src, new HashMap<>())
                            .getOrDefault(dst, new MessageCount());
                    String str_msg = String.format("%s,%s,%d,%d,%d,%d,%d,%d",
                            src, dst,
                            e2e_msgs.get(src).get(dst).getAllMsgCount(),
                            e2e_msgs.get(src).get(dst).getBackgroundMsgCount(),
                            e2e_msgs.get(src).get(dst).getForegroundMsgCount(),
                            h2h_count.getAllMsgCount(),
                            h2h_count.getBackgroundMsgCount(),
                            h2h_count.getForegroundMsgCount());
                    writer.write(str_msg + "\n");
                }
            }
            for (Map.Entry<String, HashMap<String, MessageCount>> entry : h2h_msgs.entrySet()) {
                String src = entry.getKey();
                for (String dst : entry.getValue().keySet()) {
                    MessageCount e2e_count = e2e_msgs.getOrDefault(src, new HashMap<>())
                            .getOrDefault(dst, new MessageCount());
                    if (e2e_count.getAllMsgCount() != 0) {
                        continue;
                    }
                    String str_msg = String.format("%s,%s,%d,%d,%d,%d,%d,%d",
                            src, dst,
                            e2e_count.getAllMsgCount(),
                            e2e_count.getBackgroundMsgCount(),
                            e2e_count.getForegroundMsgCount(),
                            h2h_msgs.get(src).get(dst).getAllMsgCount(),
                            h2h_msgs.get(src).get(dst).getBackgroundMsgCount(),
                            h2h_msgs.get(src).get(dst).getForegroundMsgCount());
                    writer.write(str_msg + "\n");
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getE2EMsgCount() {
        return _end2end_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getAllMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public List<Integer> getE2EMsgBackgroundCount() {
        return _end2end_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getBackgroundMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public List<Integer> getE2EMsgForegroundCount() {
        return _end2end_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getForegroundMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public List<Integer> getH2HMsgCount() {
        return _hop2hop_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getAllMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public List<Integer> getH2HMsgBackgroundCount() {
        return _hop2hop_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getBackgroundMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public List<Integer> getH2HMsgForegroundCount() {
        return _hop2hop_msgs.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(MessageCount::getForegroundMsgCount).boxed()
                .collect(Collectors.toList());
    }

    public void printMsgSummary(String type) {
        List<Integer> msgs;
        if (type.equals("h2h")) {
            msgs = getH2HMsgCount();
        } else if (type.equals("h2h_bg")) {
            msgs = getH2HMsgBackgroundCount();
        } else if (type.equals("h2h_fg")) {
            msgs = getH2HMsgForegroundCount();
        } else if (type.equals("e2e")) {
            msgs = getE2EMsgCount();
        } else if (type.equals("e2e_bg")) {
            msgs = getE2EMsgBackgroundCount();
        } else if (type.equals("e2e_fg")) {
            msgs = getE2EMsgForegroundCount();
        } else {
            System.out.printf("[ERROR] #NMPT Try to print unknown type of message summary. ");
            return;
        }

        int sum = msgs.stream().mapToInt(Integer::intValue).sum();
        double mean = msgs.stream().mapToInt(Integer::intValue).average().getAsDouble();
        double std = Math.sqrt(msgs.stream()
                .map(i -> i - mean)
                .map(i -> i * i)
                .mapToDouble(i -> i).average().getAsDouble());
        int min = msgs.stream().mapToInt(Integer::intValue).min().getAsInt();
        int max = msgs.stream().mapToInt(Integer::intValue).max().getAsInt();
        System.out.println("========== Summary of " + type + " messages ===========");
        System.out.println(String.format(
                "    Total %d messages from %d members: mean %.2f, std %.2f, min %d, max %d",
                sum, _end2end_msgs.size(), mean, std, min, max));
    }

    public void printAllMessageSummary() {
        printMsgSummary("e2e");
        printMsgSummary("e2e_bg");
        printMsgSummary("e2e_fg");
        printMsgSummary("h2h");
        printMsgSummary("h2h_bg");
        printMsgSummary("h2h_fg");
    }

    /** Utility function: make deep copy for msg records */
    private HashMap<String, HashMap<String, MessageCount>> msgRecordDeepCopy(
            HashMap<String, HashMap<String, MessageCount>> msgs) {
        HashMap<String, HashMap<String, MessageCount>> res = new HashMap<>();
        for (String src : msgs.keySet()) {
            res.put(src, new HashMap<>());
            for (String dst : res.get(src).keySet()) {
                res.get(src).put(dst, new MessageCount(msgs.get(src).get(dst)));
            }
        }
        return res;
    }

    private HashMap<String, HashMap<String, MessageCount>> getMsgDiff(
            HashMap<String, HashMap<String, MessageCount>> cur_msgs,
            HashMap<String, HashMap<String, MessageCount>> pre_msgs) {
        HashMap<String, HashMap<String, MessageCount>> diff = new HashMap<>();
        for (String src : cur_msgs.keySet()) {
            diff.put(src, new HashMap<>());
            for (String dst : cur_msgs.get(src).keySet()) {
                MessageCount cur_cnt = cur_msgs.get(src).get(dst);
                if (!pre_msgs.containsKey(src) || !pre_msgs.get(src).containsKey(dst)) {
                    diff.get(src).put(dst, new MessageCount(cur_cnt));
                } else {
                    diff.get(src).put(dst, cur_cnt.minus(pre_msgs.get(src).get(dst)));
                }
            }
        }
        return diff;
    }

    private void recordCheckpointTime(int ts, String fname) {
        try {
            FileWriter writer = new FileWriter(fname, true);
            writer.write("Total time: " + (ts - checkpoint_start_time));
            System.out.println("====== Total checkpoint time " + (ts - checkpoint_start_time));
            writer.close();
        } catch (IOException e) {
            System.out.println("[ERROR] #BWTM Failed to write checkpointing time to file.");
        }
    }

    /**
     * Make checkpoint for end-to-end and hop-to-hop message records,
     * and records the difference among current record - checkpoint into
     * fname. Note that this function only maintains the most recent
     * checkpoint.
     */
    public void msgCheckpoint(int ts, String fname) {
        if (_pre_end2end_msgs.isEmpty()) { // The first time to do checkpointing
            recordRawMsgData(fname);
        } else {
            HashMap<String, HashMap<String, MessageCount>> diff_e2e = getMsgDiff(_end2end_msgs, _pre_end2end_msgs);
            HashMap<String, HashMap<String, MessageCount>> diff_h2h = getMsgDiff(_hop2hop_msgs, _pre_hop2hop_msgs);
            recordRawMsgData(fname, diff_e2e, diff_h2h);
        }
        recordCheckpointTime(ts, fname);
        _pre_end2end_msgs = msgRecordDeepCopy(_end2end_msgs);
        _pre_hop2hop_msgs = msgRecordDeepCopy(_hop2hop_msgs);
        checkpoint_start_time = ts;
    }

}
