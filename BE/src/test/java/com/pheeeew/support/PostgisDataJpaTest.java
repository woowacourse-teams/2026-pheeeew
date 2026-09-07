package com.pheeeew.support;

import com.pheeeew.common.config.JpaAuditingConfig;
import com.pheeeew.report.application.SighReportService;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.infra.KoreanSighNicknameGenerator;
import com.pheeeew.sigh.infra.PostgisSighLocationGenerator;
import com.pheeeew.sigh.infra.SighLocationConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        SharedPostgisTestConfiguration.class,
        JpaAuditingConfig.class,
        SighReportService.class,
        SighService.class,
        KoreanSighNicknameGenerator.class,
        PostgisSighLocationGenerator.class,
        SighLocationConfig.class
})
public @interface PostgisDataJpaTest {
}
