package com.pheeeew.emotion.application;

import org.locationtech.jts.geom.Point;

public interface EmotionLocationGenerator {

    Point generate(double longitude, double latitude);
}
