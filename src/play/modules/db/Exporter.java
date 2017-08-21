/**
 *
 * Copyright 2010, Nicolas Leroux.
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * User: ${user}
 * Date: ${date}
 *
 */
package play.modules.db;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import javax.persistence.Entity;
import javax.persistence.PersistenceUnit;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaExport.Action;
import org.hibernate.tool.schema.TargetType;

import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses;
import play.classloading.ApplicationClassloader;
import play.db.Configuration;
import play.db.DB;
import play.db.DBPlugin;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.vfs.VirtualFile;

public class Exporter {

	public static void main(String[] args) throws Exception {

		File root = new File(System.getProperty("application.path"));
		String id = System.getProperty("play.id", "");
//		Play.init(root, id);
		startDBPlugin(root, id);

//		boolean script = true;
		boolean drop = false;
		boolean create = false;
		boolean halt = false;
//		boolean export = false;
		String outFile = null;
		String importFiles = null; // "/import.sql";
		String propFile = null;
		boolean format = true;
		String delim = ";";

		EnumSet<TargetType> targetTypes = EnumSet.noneOf(TargetType.class);
		targetTypes.add(TargetType.STDOUT);

		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("--")) {
				if (args[i].equals("--drop")) {
					drop = true;
				} else if (args[i].equals("--create")) {
					create = true;
				} else if (args[i].equals("--haltonerror")) {
					halt = true;
//				} else if (args[i].equals("--export")) {
//					export = true;				
//				} else if (args[i].equals("--quiet")) {
//					script = false;
				} else if (args[i].startsWith("--output=")) {
					targetTypes.add(TargetType.SCRIPT);
					outFile = args[i].substring(9);
				} else if (args[i].startsWith("--import=")) {
					importFiles = args[i].substring(9);
				} else if (args[i].startsWith("--properties=")) {
					propFile = args[i].substring(13);
				} else if (args[i].equals("--noformat")) {
					format = false;
				} else if (args[i].startsWith("--delimiter=")) {
					delim = args[i].substring(12);
//				} else if (args[i].startsWith("--config=")) {
//					cfg.configure(args[i].substring(9));
				} else if (args[i].startsWith("--naming=")) {
					// cfg.setNamingStrategy(
					// (NamingStrategy) ReflectHelper.classForName(args[i].substring(9))
					// .newInstance()
					// );
				}
			}
		}
		
		String dbName = JPA.DEFAULT;
		
		Configuration dbConfig = new Configuration(dbName);
		
		// FIXME: Do we need this or should it be passed into the service registry?
		Thread.currentThread().setContextClassLoader(Play.classloader);
		
		StandardServiceRegistryBuilder serviceRegistryBuilder = new StandardServiceRegistryBuilder()
				.applySetting(AvailableSettings.CLASSLOADERS, new HashSet<ClassLoader>(Arrays.asList(Play.classloader)))
				.applySettings(properties(dbName, dbConfig))
//		        .applySetting(AvailableSettings.HBM2DDL_CONNECTION, connection)
		        .applySetting(AvailableSettings.HBM2DDL_AUTO, "create");
		
		if (propFile != null) {
			Properties props = new Properties();
			props.load(new FileInputStream(propFile));
			serviceRegistryBuilder.applySettings(props);
		}
		
		MetadataSources metadata = new MetadataSources(serviceRegistryBuilder.build());
			    
		// [...] adding annotated classes to metadata here...
//		List<Class> entities = Play.classloader.getAnnotatedClasses(Entity.class);
		List<Class> entities = entityClasses(dbName);
		for (Class _class : entities) {
			metadata.addAnnotatedClass(_class);
		}

		SchemaExport se = new SchemaExport()
				.setHaltOnError(halt)
				.setDelimiter(delim)
				.setFormat(format);
		
		if (outFile != null) {
			se.setOutputFile(outFile);
		}
		
		if (importFiles != null) {
			se.setImportFiles(importFiles);
		}
		
		Action action = (drop && create) ? Action.BOTH : drop ? Action.DROP : create ? Action.CREATE : Action.NONE;

		// se.execute(script, export, drop, create);
		se.execute(targetTypes, action, metadata.buildMetadata());
	}
	
	/**
	 * NOTE: This has been copied from play.db.Evolutions.main(String[])
	 */
	private static void startDBPlugin(File root, String id) {
        /** Start the DB plugin **/
		Play.id = id;
	    Play.started = false;
	    Play.applicationPath = root;
        Play.guessFrameworkPath();
        Play.readConfiguration();
        Play.classes = new ApplicationClasses();
        Play.classloader = new ApplicationClassloader();
        
        VirtualFile appRoot = VirtualFile.open(Play.applicationPath);
        Play.roots.clear();
        Play.roots.add(appRoot);
        Play.javaPath.clear();
        Play.javaPath.add(appRoot.child("app"));
        Play.javaPath.add(appRoot.child("conf"));

        Play.loadModules(VirtualFile.open(Play.applicationPath));

        Logger.init();
        Logger.setUp("ERROR");
        new DBPlugin().onApplicationStart();
	}
	
    /**
     * @param dbName
     * @param dbConfig
     * @return
     * 
     * NOTE: This has been copied from play.db.jpa.JPAPlugin.properties(String dbName, Configuration dbConfig)
     */
    private static Properties properties(String dbName, Configuration dbConfig) {
        Properties properties = new Properties();
        properties.putAll(dbConfig.getProperties());
        properties.put("javax.persistence.transaction", "RESOURCE_LOCAL");
        properties.put("javax.persistence.provider", "org.hibernate.ejb.HibernatePersistence");
        properties.put("hibernate.dialect", JPAPlugin.getDefaultDialect(dbConfig, dbConfig.getProperty("db.driver")));

        if (!dbConfig.getProperty("jpa.ddl", Play.mode.isDev() ? "update" : "none").equals("none")) {
            properties.setProperty("hibernate.hbm2ddl.auto", dbConfig.getProperty("jpa.ddl", "update"));
        }

        properties.put("hibernate.connection.datasource", DB.getDataSource(dbName));
        
        return properties;
    }
    
    /**
     * @param dbName
     * @return
     * 
     * NOTE: This has been copied from play.db.jpa.JPAPlugin.entityClasses(String dbName)
     */
    private static List<Class> entityClasses(String dbName) {
        List<Class> entityClasses = new ArrayList<>();
        
        List<Class> classes = Play.classloader.getAnnotatedClasses(Entity.class);
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                // Do we have a transactional annotation matching our dbname?
                PersistenceUnit pu = clazz.getAnnotation(PersistenceUnit.class);
                if (pu != null && pu.name().equals(dbName)) {
                    entityClasses.add(clazz);
                } else if (pu == null && JPA.DEFAULT.equals(dbName)) {
                    entityClasses.add(clazz);
                }                    
            }
        }

        // Add entities
        String[] moreEntities = Play.configuration.getProperty("jpa.entities", "").split(", ");
        for (String entity : moreEntities) {
            if (entity.trim().equals("")) {
                continue;
            }
            try {
                Class<?> clazz = Play.classloader.loadClass(entity);  
                // Do we have a transactional annotation matching our dbname?
                PersistenceUnit pu = clazz.getAnnotation(PersistenceUnit.class);
                if (pu != null && pu.name().equals(dbName)) {
                    entityClasses.add(clazz);
                } else if (pu == null && JPA.DEFAULT.equals(dbName)) {
                    entityClasses.add(clazz);
                }         
            } catch (Exception e) {
                Logger.warn(e, "JPA -> Entity not found: %s", entity);
            }
        }
        
        return entityClasses;
    }
}
