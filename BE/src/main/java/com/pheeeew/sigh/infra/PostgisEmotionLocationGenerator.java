package com.pheeeew.sigh.infra;

import com.pheeeew.sigh.application.EmotionLocationGenerator;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.domain.repository.projection.GeneratedLocation;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PostgisEmotionLocationGenerator implements EmotionLocationGenerator {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private final EmotionRepository emotionRepository;
    private final SecureRandom random;

    @Override
    public Point generate(double longitude, double latitude) {
        var offset = EmotionLocationOffsetCalculator.calculate(random.nextDouble(), random.nextDouble());
        GeneratedLocation location = emotionRepository.findGeneratedLocation(
                longitude, latitude, offset.eastingMeters(), offset.northingMeters()
        );

        return GEOMETRY_FACTORY.createPoint(new Coordinate(location.getLongitude(), location.getLatitude()));
    }
}
