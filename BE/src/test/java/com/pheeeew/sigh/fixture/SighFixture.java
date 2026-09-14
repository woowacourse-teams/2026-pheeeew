package com.pheeeew.sigh.fixture;

import com.pheeeew.sigh.domain.Sigh;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;

public final class SighFixture {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private SighFixture() {
    }

    public static Sigh.SighBuilder 기본_한숨_빌더() {
        return Sigh.builder()
                .requestId(UUID.randomUUID())
                .location(서울시청_좌표())
                .nickname("외로운 회사원");
    }

    public static Sigh.SighBuilder 기기가_있는_한숨_빌더(Long deviceId) {
        return 기본_한숨_빌더().deviceId(deviceId);
    }

    public static Point 서울시청_좌표() {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(126.9774, 37.5669));
    }

    public static Point 서울시청_좌표(int srid) {
        Point point = new GeometryFactory().createPoint(new Coordinate(126.9774, 37.5669));
        point.setSRID(srid);
        return point;
    }

    public static Sigh 저장된_기본_한숨(Long id, Instant createdAt) {
        Sigh sigh = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(sigh, "id", id);
        ReflectionTestUtils.setField(sigh, "createdAt", createdAt);
        return sigh;
    }
}
