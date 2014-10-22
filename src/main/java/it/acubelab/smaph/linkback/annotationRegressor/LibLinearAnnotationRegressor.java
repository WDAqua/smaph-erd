package it.acubelab.smaph.linkback.annotationRegressor;

import it.acubelab.smaph.learn.LibLinearModel;

public class LibLinearAnnotationRegressor extends LibLinearModel implements AnnotationRegressor{

	public LibLinearAnnotationRegressor(String modelFile) {
		super(modelFile);
	}
	
}
