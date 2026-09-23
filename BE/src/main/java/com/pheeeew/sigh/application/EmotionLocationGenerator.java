package com.pheeeew.sigh.application;

import org.locationtech.jts.geom.Point;

public interface EmotionLocationGenerator {

    Point generate(double longitude, double latitude);
}
