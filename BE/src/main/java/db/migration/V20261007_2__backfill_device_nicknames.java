package db.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import koreannickname.KoreanNicknameGenerator;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V20261007_2__backfill_device_nicknames extends BaseJavaMigration {

    private static final int BATCH_SIZE = 100;

    private final List<String> modifiers;
    private final List<String> characters;

    public V20261007_2__backfill_device_nicknames() {
        this(readWords("modifiers.txt"), readWords("characters.txt"));
    }

    V20261007_2__backfill_device_nicknames(List<String> modifiers, List<String> characters) {
        this.modifiers = modifiers;
        this.characters = characters;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout = '5s'");
            // 백필 대상과 닉네임이 저장 도중 바뀌지 않도록 등록·수정을 막는다.
            statement.execute("LOCK TABLE devices IN SHARE ROW EXCLUSIVE MODE");
        }

        // 새로 추가한 컬럼은 모두 NULL이다. 감정의 기존 표시 이름은 사용하지 않는다.
        int combinations = Math.multiplyExact(modifiers.size(), characters.size());
        Iterator<String> nicknames = IntStream.range(0, combinations)
                .mapToObj(index -> modifiers.get(index / characters.size()) + " "
                        + characters.get(index % characters.size()))
                .iterator();
        try (var select = connection.createStatement()) {
            // Flyway 트랜잭션의 커서로 대상 ID도 배치 단위로 가져온다.
            select.setFetchSize(BATCH_SIZE);
            try (var devices = select.executeQuery("SELECT id FROM devices WHERE nickname IS NULL ORDER BY id");
                 var update = connection.prepareStatement("""
                         UPDATE devices SET nickname = ?
                         WHERE id = ? AND nickname IS NULL
                         """)) {
                int pending = 0;
                while (devices.next()) {
                    if (!nicknames.hasNext()) {
                        // 앞서 제출한 배치까지 Flyway의 전체 트랜잭션에서 롤백한다.
                        throw new SQLException("Device nickname backfill exhausted its available combinations");
                    }
                    update.setString(1, nicknames.next());
                    update.setLong(2, devices.getLong("id"));
                    update.addBatch();
                    if (++pending == BATCH_SIZE) {
                        executeBatch(update, pending);
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    executeBatch(update, pending);
                }
            }
        }
    }

    private static List<String> readWords(String resource) {
        // 0.1.1에 포함된 사전을 같은 규칙으로 읽는다. 전체 조합 목록은 만들지 않는다.
        try (var input = KoreanNicknameGenerator.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Nickname dictionary is missing: " + resource);
            }
            List<String> words = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(word -> !word.isEmpty() && !word.startsWith("#"))
                    .toList();
            if (words.isEmpty()) {
                throw new IllegalStateException("Nickname dictionary is empty: " + resource);
            }
            return words;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read nickname dictionary: " + resource, exception);
        }
    }

    private void executeBatch(PreparedStatement update, int expected) throws SQLException {
        int[] counts = update.executeBatch();
        if (counts.length != expected) {
            throw new SQLException("Device nickname backfill returned an unexpected batch result size");
        }
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new SQLException("Device nickname backfill did not update every target");
            }
        }
        update.clearBatch();
    }
}
