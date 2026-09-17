package com.pheeeew.sigh.infra;

import com.pheeeew.sigh.application.SighLocationGenerator;
import com.pheeeew.sigh.domain.repository.SighRepository;
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
public class PostgisSighLocationGenerator implements SighLocationGenerator {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private final SighRepository sighRepository;
    private final SecureRandom random;

    @Override
    public Point generate(double longitude, double latitude) {
        var offset = SighLocationOffsetCalculator.calculate(random.nextDouble(), random.nextDouble());
        GeneratedLocation location = sighRepository.findGeneratedLocation(
                longitude, latitude, offset.eastingMeters(), offset.northingMeters()
        );

        return GEOMETRY_FACTORY.createPoint(new Coordinate(location.getLongitude(), location.getLatitude()));
    }
}
