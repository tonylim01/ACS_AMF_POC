package media.platform.amf.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);

    private static TransactionManager instance = null;

    public static TransactionManager getInstance() {
        if (instance == null) {
            instance = new TransactionManager();
        }

        return instance;
    }

    private Map<String, String> transactions;

    public TransactionManager() {
        transactions = new HashMap<>();
    }

    public void put(String transactionId, String sessionId) {
        if ((transactionId != null) && (sessionId != null) && !transactions.containsKey(transactionId)) {
            transactions.put(transactionId, sessionId);
        }
    }

    public String get(String transactionId) {
        String value = null;

        if (transactionId != null && transactions.containsKey(transactionId)) {
            value = transactions.get(transactionId);
            transactions.remove(transactionId);
        }

        return value;
    }

    public void delete(String transactionId) {
        if ((transactionId != null) && transactions.containsKey(transactionId)) {
            transactions.remove(transactionId);
        }
    }
}
