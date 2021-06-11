package com.udacity.catpoint.imageservice.service;

import java.awt.image.BufferedImage;

public interface ImageService {
    boolean imageContainsCat(BufferedImage image, float confidenceThreshhold);
}
