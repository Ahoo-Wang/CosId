package me.ahoo.cosid.mysql;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySqlIdSegmentDistributorTest {
    private MySqlIdSegmentDistributor mySqlIdSegmentDistributor;
    private JdbcTemplate jdbcTemplate;
    private JdbcTransactionManager jdbcTransactionManager;

    @BeforeAll
    private void init() {
        MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
        dataSource.setUser("root");
        dataSource.setPassword("root");
        dataSource.setDatabaseName("test_db");
        dataSource.setUrl("jdbc:mysql://localhost:3306/test_db?serverTimezone=GMT%2B8&characterEncoding=utf-8");
        jdbcTransactionManager = new JdbcTransactionManager(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);

        mySqlIdSegmentDistributor = new MySqlIdSegmentDistributor("test", "test", 100, jdbcTemplate, jdbcTransactionManager);
    }

    @Test
    void nextMaxId() {
        long id = mySqlIdSegmentDistributor.nextMaxId(100);
        Assertions.assertNotNull(id);
    }

    @Test
    void missing() {
        MySqlIdSegmentDistributor missingDistributor = new MySqlIdSegmentDistributor("test", UUID.randomUUID().toString(), 10, jdbcTemplate, jdbcTransactionManager);
        Assertions.assertThrows(NameMissingException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                long id = missingDistributor.nextMaxId(100);
            }
        });
    }

    @Test
    void missing_init() {
        MySqlIdSegmentDistributor missingDistributor = new MySqlIdSegmentDistributor("test", UUID.randomUUID().toString(), 10, jdbcTemplate, jdbcTransactionManager);
        Assertions.assertThrows(NameMissingException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                long id = missingDistributor.nextMaxId(100);
            }
        });
        missingDistributor.initSegment(0);
        long id = missingDistributor.nextMaxId(100);
        Assertions.assertEquals(100, id);
        Assertions.assertThrows(DuplicateKeyException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                missingDistributor.initSegment(0);
            }
        });
    }

    static final int CONCURRENT_THREADS = 20;
    static final int THREAD_REQUEST_NUM = 5000;

    @Test
    public void concurrent_generate_step_100() {
        String namespace = UUID.randomUUID().toString();
        MySqlIdSegmentDistributor maxIdDistributor_generate_step_100 = new MySqlIdSegmentDistributor(namespace, UUID.randomUUID().toString(), 100, jdbcTemplate, jdbcTransactionManager);
        maxIdDistributor_generate_step_100.initSegment(0);
        SegmentId defaultSegmentId = new DefaultSegmentId(maxIdDistributor_generate_step_100);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = defaultSegmentId.generate();
                    ids.add(id);
                }
                return ids;
            });

            threads++;
        }
        CompletableFuture.allOf(completableFutures).thenAccept(nil -> {
            List<Long> totalIds = new ArrayList<>();
            for (CompletableFuture<List<Long>> completableFuture : completableFutures) {
                List<Long> ids = completableFuture.join();
                totalIds.addAll(ids);
            }
            totalIds.sort(Long::compareTo);
            Long lastId = null;
            for (Long currentId : totalIds) {
                if (lastId == null) {
                    Assertions.assertEquals(1, currentId);
                    lastId = currentId;
                    continue;
                }

                Assertions.assertEquals(lastId + 1, currentId);
                lastId = currentId;
            }

            Assertions.assertEquals(THREAD_REQUEST_NUM * CONCURRENT_THREADS, lastId);
        }).join();
    }


}
