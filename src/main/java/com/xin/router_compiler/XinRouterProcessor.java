package com.xin.router_compiler;

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import com.xin.router.annotations.RouterUtils;
import com.xin.router.annotations.XinRouterHost;
import com.xin.router.annotations.XinRouterPath;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

import static jdk.nashorn.internal.objects.Global.print;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class XinRouterProcessor extends AbstractProcessor {


    private static final String FILE_DOC = "AUTO-GENERATED FILE.  DO NOT MODIFY.";
    private Elements elementUtils;
    private Filer filer;
    private ClassName SENDER_CLASS_NAME = ClassName.get("com.xin.router", "Sender");


    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        elementUtils = processingEnvironment.getElementUtils();
        filer = processingEnvironment.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotation : getSupportedAnnotations()) {
            types.add(annotation.getCanonicalName());
        }
        return types;
    }

    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        annotations.add(XinRouterHost.class);
        return annotations;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        List<JavaFile> files = new ArrayList<>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(XinRouterHost.class)) {
            if (!SuperficialValidation.validateElement(element))
                continue;
            processRouter(files, element);


        }

        for (JavaFile javaFile : files) {
            try {
                javaFile.writeTo(filer);
            } catch (IOException e) {
                print("Unable to write same name %s: %s", javaFile.packageName, e.getMessage());
            }
        }
        return false;
    }

    private void processRouter(List<JavaFile> files, Element element) {
        List<? extends Element> allEle = element.getEnclosedElements();

        XinRouterHost xinRouter = element.getAnnotation(XinRouterHost.class);
        String host = xinRouter.host();
        if (RouterUtils.isEmpty(host))
            return;

        // constructor build
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder();
        constructorBuilder.addModifiers(Modifier.PUBLIC).addException(Exception.class);


        ClassName original = ClassName.get(elementUtils.getPackageOf(element).toString(), element.getSimpleName().toString());

        constructorBuilder.addStatement("this.realTarget = $T.class.newInstance()", original)
                .addStatement("this.methodName = new $T()", HashMap.class);


        // parse RouterPath
        int size = allEle.size();
        for (int i = 0; i < size; i++) {

            Element subElement = allEle.get(i);
            XinRouterPath pathAnnotations = subElement.getAnnotation(XinRouterPath.class);
            if (pathAnnotations == null)
                continue;

            String methodFullTypes = subElement.toString();
            int start = methodFullTypes.indexOf("(");
            int end = methodFullTypes.indexOf(")");
            if (end - start > 1) {
                // open1(java.lang.String,com.tangxiaolv.router.Promise) =>
                // ,java.lang.String.class,com.tangxiaolv.router.Promise.class))
                String types = methodFullTypes.substring(start + 1, end);
                if (types.lastIndexOf("...") != -1)
                    types = types.replace("...", "[]");
                methodFullTypes = "," + RouterUtils.getFullTypesString(types) + "))";
            } else {
                methodFullTypes = "))";
            }

             Name methodName = subElement.getSimpleName();
            String methodKey = pathAnnotations.path();
            constructorBuilder.addStatement("this.methodName.put($S,realTarget.getClass().getMethod($S" + methodFullTypes,
                    methodKey, methodName);

            String parameterKey = RouterUtils.getParameterKey(pathAnnotations.path());
            String parametersName = ((ExecutableElement) subElement).getParameters().toString();
            constructorBuilder.addStatement("this.methodName.put($S,$S)", parameterKey, parametersName);

        }


        // method build
        MethodSpec.Builder invokeBuilder = MethodSpec.methodBuilder("invoke");
        invokeBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(Object.class)
                .addParameter(String.class, "path")
                .addParameter(HashMap.class, "parameter")
                .addException(NoSuchMethodException.class);

        // return Sender.execute(path, realTarget, methodName, parameter);

        invokeBuilder.addStatement("return $T.execute(path, realTarget, methodName, parameter)", SENDER_CLASS_NAME);


        TypeSpec clazz = TypeSpec.classBuilder(RouterUtils.getBridgeClassName(host))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                // Fields
                .addFields(buildRouterModuleFields())
                // constructor
                .addMethod(constructorBuilder.build())
                // Methods
                .addMethod(invokeBuilder.build())
                // doc
                .addJavadoc(FILE_DOC)
                .build();

        JavaFile javaFile = JavaFile.builder(RouterUtils.getPagkageName(), clazz).build();
        files.add(javaFile);

    }

    private Iterable<FieldSpec> buildRouterModuleFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        FieldSpec realTarget = FieldSpec.builder(Object.class, "realTarget", Modifier.PRIVATE).build();
        fieldSpecs.add(realTarget);

        FieldSpec methodName = FieldSpec.builder(HashMap.class, "methodName", Modifier.PRIVATE).build();
        fieldSpecs.add(methodName);
        return fieldSpecs;

    }

}

