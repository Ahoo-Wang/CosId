package me.ahoo.cosid.mysql;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * @author ahoo wang
 */
public class MySqlIdSegmentDistributor implements IdSegmentDistributor {
    private final String namespace;
    private final String name;
    private final int step;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager platformTransactionManager;

    public MySqlIdSegmentDistributor(String namespace, String name, int step, JdbcTemplate jdbcTemplate, PlatformTransactionManager platformTransactionManager) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.jdbcTemplate = jdbcTemplate;
        this.platformTransactionManager = platformTransactionManager;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getStep() {
        return step;
    }

    private static final String ACC_MAX_ID_SQL = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";

    private static final String FETCH_MAX_ID_SQL = "select last_max_id from cosid where name = ? limit 1;";

    @Override
    public long nextMaxId(int step) {
        TransactionStatus transactionStatus = platformTransactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            int affected = jdbcTemplate.update(ACC_MAX_ID_SQL, step, getNamespacedName());
            if (affected == 0) {
                throw new NameMissingException(getNamespacedName());
            }
            return jdbcTemplate.queryForObject(FETCH_MAX_ID_SQL, Long.class, getNamespacedName());
        } finally {
            platformTransactionManager.commit(transactionStatus);
        }
    }

    private static final String INIT_SEGMENT_SQL = "insert into cosid (name, last_max_id) value (?, ?);";

    public void initSegment(long offset) throws DuplicateKeyException {
        jdbcTemplate.update(INIT_SEGMENT_SQL, getNamespacedName(), offset);
    }
}
