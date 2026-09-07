package com.pheeeew.sigh.infra;

import com.pheeeew.sigh.application.SighLocationGenerator;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.projection.GeneratedLocation;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostgisSighLocationGenerator implements SighLocationGenerator {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private final SighRepository sighRepository;
    private final RandomGenerator random;

    @Autowired
    public PostgisSighLocationGenerator(SighRepository sighRepository) {
        this(sighRepository, new SecureRandom());
    }

    PostgisSighLocationGenerator(SighRepository sighRepository, RandomGenerator random) {
        this.sighRepository = sighRepository;
        this.random = random;
    }

    @Override
    public Point generate(double longitude, double latitude) {
        UniformDiskSampler.Offset offset = UniformDiskSampler.sample(random.nextDouble(), random.nextDouble());
        GeneratedLocation location = sighRepository.findGeneratedLocation(
                longitude, latitude, offset.eastingMeters(), offset.northingMeters()
        );

        return GEOMETRY_FACTORY.createPoint(
                new Coordinate(location.getLongitude(), location.getLatitude())
        );
    }
}
