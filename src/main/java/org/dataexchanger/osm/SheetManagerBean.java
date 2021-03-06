package org.dataexchanger.osm;

import org.dataexchanger.osm.annotations.Id;
import org.dataexchanger.osm.annotations.SheetEntity;
import org.dataexchanger.osm.exceptions.InvalidPackageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

public class SheetManagerBean implements SheetManager {

    private static final Logger logger = LoggerFactory.getLogger(SheetManager.class);
    private final Map<String, List<String>> mappedColumnNames;
    private final Map<String, Class<?>> scannedSheetEntities;
    private final Map<String, List<String>> methodNames;

    public SheetManagerBean() {
        mappedColumnNames = new HashMap<String, List<String>>();
        scannedSheetEntities = new HashMap<String, Class<?>>();
        methodNames = new HashMap<>();
    }

    @Override
    public void scanMappedPackages(String... packages) throws IOException, ClassNotFoundException {
        logger.info("Scanning sheet entities");
        Stream.of(packages).forEach(pkg -> {
            try {
                scanClasses(pkg);
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        });
        logger.info("Sheet entities scanning complete");
    }
    
    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private void scanClasses(String packageName)
            throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        if (resources.hasMoreElements() == false) {
            String message = String.format("Package \"%s\" could not be found in classpath.", packageName);
            logger.error(message);
            throw new InvalidPackageException(message);
        }
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        for (File directory : dirs) {
            findClasses(directory, packageName);
        }
    }
    
    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    private void findClasses(File directory, String packageName) throws ClassNotFoundException {
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String fullyQualifyingClassName = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                Class aClass = Class.forName(fullyQualifyingClassName);

                if (aClass.isAnnotationPresent(SheetEntity.class)) {
                    scannedSheetEntities.put(fullyQualifyingClassName, aClass);
                    scanColumnNames(aClass, packageName);
                }
            }
        }
    }

    private void scanGetterMethods(Class<?> aClass, String basePackageName) {
        List<String> methodList = new ArrayList<>();
        Method[] getters = aClass.getDeclaredMethods();
        for (Method getter : getters) {
            methodList.add(getter.getName());
        }
        methodNames.put(aClass.getName(), methodList);
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param aClass   The class to be scanned to get the sheet column names
     * @param basePackageName The package name for mapped classes
     * @return The classes
     * @throws ClassNotFoundException
     */
    private void scanColumnNames(Class<?> aClass, String basePackageName) throws ClassNotFoundException {
        List<String> columnNames = new LinkedList<String>();
        Field[] fields = aClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().getName().startsWith(basePackageName)) {
                Class aggragatedClass = Class.forName(field.getType().getName());

                Field[] aggragatedFields = aggragatedClass.getDeclaredFields();
                for (Field aggragatedField : aggragatedFields) {
                    Annotation aggragatedFieldAnnotation = aggragatedField.getAnnotation(Id.class);
                    if (aggragatedField.getAnnotation(Id.class) != null) {
                        String value = ((Id) aggragatedFieldAnnotation).value();
                        columnNames.add(field.getName() + "_" + value);
                    }
                }
            } else {
                columnNames.add(field.getName());
            }
        }
        mappedColumnNames.put(aClass.getName(), columnNames);
    }

    public Map<String, List<String>> getMappedColumnNames() {
        return this.mappedColumnNames;
    }

    public Map<String, List<String>> getMethodNames() {
        return this.methodNames;
    }

    public Map<String, Class<?>> getScannedSheetEntities() {
        return this.scannedSheetEntities;
    }
}
