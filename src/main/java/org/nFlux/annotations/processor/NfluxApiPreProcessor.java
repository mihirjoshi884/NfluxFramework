package org.nFlux.annotations.processor;

import org.nFlux.annotations.NfluxApiPreProcess;
import org.nFlux.enums.API_Type;
import org.nFlux.pojo.API;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.reflect.Field;
import java.util.Set;


@SupportedAnnotationTypes("org.nFlux.annotations.NfluxApiPreProcess")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class NfluxApiPreProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(NfluxApiPreProcess.class)) {
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement typeElement = (TypeElement) element;

                checkFields(typeElement);
            }
        }
        return true;
    }

    private void checkFields(TypeElement typeElement){

        boolean hasApiType = false;
        boolean hasDependencyIndex = false;
        boolean hasIndependentApiResponse = false;
        boolean hasResponseHandling = false;
        API_Type apiType = null;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            String fieldName = enclosedElement.getSimpleName().toString();

            switch (fieldName) {
                case "api_type":
                    hasApiType = true;
                    apiType = getApiTypeValue(enclosedElement);
                    if(apiType.equals(API_Type.DEPENDENT)){
                        hasDependencyIndex = checkDependencyIndex(enclosedElement);
                    }
                    break;

                case "independentApiResponse":
                    hasIndependentApiResponse = true;
                    break;
                case "responseHandling":
                    hasResponseHandling = true;
                    break;
            }
        }

        Messager messager = processingEnv.getMessager();
        if (apiType == API_Type.DEPENDENT) {
            if (!hasDependencyIndex) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "DEPENDENT API must have a dependencyIndex field.", typeElement);
            }
            if (!hasIndependentApiResponse) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "DEPENDENT API must have an independentApiResponse field.", typeElement);
            }
            if (!hasResponseHandling) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "DEPENDENT API must have a responseHandling field.", typeElement);
            }
        }
    }

    private API_Type getApiTypeValue(Element element) {
        for(Element enclosedElement : element.getEnclosedElements()){
            if (enclosedElement.getKind() == ElementKind.FIELD){
                if(enclosedElement.getSimpleName().toString().equals("api_type")){
                    return API_Type.valueOf(enclosedElement.getSimpleName().toString());
                }
            }
        }
        return null;
    }

    private boolean checkDependencyIndex(Element element){
        for(Element enclosedElement : element.getEnclosedElements()){
            if(enclosedElement.getKind() == ElementKind.FIELD){
                if(enclosedElement.getSimpleName().toString().equals("dependencyIndex")){
                    return true;
                }
            }
        }
        return false;
    }

}
