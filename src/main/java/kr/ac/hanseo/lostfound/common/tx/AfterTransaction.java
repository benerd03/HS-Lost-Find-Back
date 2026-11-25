package kr.ac.hanseo.lostfound.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 결과에 따라 파일 정리 같은 DB 밖 작업을 실행한다.
 * 예: 이미지를 새로 저장한 뒤 DB 저장이 실패하면 새 파일을 지우고, 성공하면 옛 파일을 지운다.
 */
public final class AfterTransaction {

    private AfterTransaction() {
    }

    public static void onCommit(Runnable action) {
        register(action, true);
    }

    public static void onRollback(Runnable action) {
        register(action, false);
    }

    private static void register(Runnable action, boolean onCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (onCommit) {
                action.run();
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean committed = status == STATUS_COMMITTED;
                if (committed == onCommit) {
                    action.run();
                }
            }
        });
    }
}
