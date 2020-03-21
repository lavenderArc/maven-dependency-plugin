package org.apache.maven.plugins.dependency.resolvers;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.fromConfiguration.MiniArtifact;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.ResolveFileFilter;
import org.apache.maven.plugins.dependency.utils.markers.SourcesFileMarkerHandler;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;

/**
 * Goal that resolves the project dependencies from the repository. When using this goal while running on Java 9 the
 * module names will be visible as well.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.0
 */
//CHECKSTYLE_OFF: LineLength
@Mojo( name = "resolve", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true )
//CHECKSTYLE_ON: LineLength
public class ResolveDependenciesMojo
    extends AbstractResolveMojo
{

    /**
     * If we should display the scope when resolving
     *
     * @since 2.0-alpha-2
     */
    @Parameter( property = "mdep.outputScope", defaultValue = "true" )
    protected boolean outputScope;

    /**
     * Only used to store results for integration test validation
     */
    DependencyStatusSets results;

    /**
     * Sort the output list of resolved artifacts alphabetically. The default ordering matches the classpath order.
     *
     * @since 2.8
     */
    @Parameter( property = "sort", defaultValue = "false" )
    boolean sort;

    /**
     * Include parent poms in the dependency resolution list.
     *
     * @since 2.8
     */
    @Parameter( property = "includeParents", defaultValue = "false" )
    boolean includeParents;

    /**
     * Projects main artifact.
     *
     * @todo this is an export variable, really
     */
    @Parameter( defaultValue = "${project.artifact}", readonly = true, required = true )
    private Artifact projectArtifact;

    /**
     * Projects main artifact.
     *
     * @todo this is an export variable, really
     */
    @Parameter( defaultValue = "${env.PWD}", readonly = true, required = true )
    private String prjDir;

    /**
     * Output dependency file type. Choose from ( json = json, yaml = yaml, all = json + yaml, none = none)
     *
     */
    @Parameter( property = "outputType", defaultValue = "none" )
    private String outputType;

    /**
     * flag to append the prjDir File
     */
    private boolean appendStatus = false;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {
        // get sets of dependencies
        results = this.getDependencySets( false, includeParents );

        String output = getOutput( outputAbsoluteArtifactFilename, outputScope, sort );
        try
        {
            if ( outputFile == null )
            {
                DependencyUtil.log( output, getLog() );
            }
            else
            {
                DependencyUtil.write( output, outputFile, appendOutput, getLog() );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /**
     * @return Returns the results.
     */
    public DependencyStatusSets getResults()
    {
        return this.results;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter()
    {
        return new ResolveFileFilter( new SourcesFileMarkerHandler( this.markersDirectory ) );
    }

    /**
     * @param outputAbsoluteArtifactFilename absolute artfiact filename.
     * @param theOutputScope The output scope.
     * @param theSort sort yes/no.
     * @return The output.
     */
    public String getOutput( boolean outputAbsoluteArtifactFilename, boolean theOutputScope, boolean theSort )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( System.lineSeparator() );
        sb.append( "The following files have been resolved:" );
        sb.append( System.lineSeparator() );
        if ( results.getResolvedDependencies() == null || results.getResolvedDependencies().isEmpty() )
        {
            sb.append( "   none" );
            sb.append( System.lineSeparator() );
        }
        else
        {
            sb.append( buildArtifactListOutput( results.getResolvedDependencies(), outputAbsoluteArtifactFilename,
                                                theOutputScope, theSort ) );
            System.out.println( outputType );
            if ( !outputType.equalsIgnoreCase( "none" ) )
            {
                extractJars( results.getResolvedDependencies(), outputAbsoluteArtifactFilename,
                        theOutputScope, theSort );
            }
        }

        if ( results.getSkippedDependencies() != null && !results.getSkippedDependencies().isEmpty() )
        {
            sb.append( System.lineSeparator() );
            sb.append( "The following files were skipped:" );
            sb.append( System.lineSeparator() );
            Set<Artifact> skippedDependencies = new LinkedHashSet<>();
            skippedDependencies.addAll( results.getSkippedDependencies() );
            sb.append( buildArtifactListOutput( skippedDependencies, outputAbsoluteArtifactFilename, theOutputScope,
                                                theSort ) );
        }

        if ( results.getUnResolvedDependencies() != null && !results.getUnResolvedDependencies().isEmpty() )
        {
            sb.append( System.lineSeparator() );
            sb.append( "The following files have NOT been resolved:" );
            sb.append( System.lineSeparator() );
            Set<Artifact> unResolvedDependencies = new LinkedHashSet<>();
            unResolvedDependencies.addAll( results.getUnResolvedDependencies() );
            sb.append( buildArtifactListOutput( unResolvedDependencies, outputAbsoluteArtifactFilename, theOutputScope,
                                                theSort ) );
        }
        sb.append( System.lineSeparator() );

        return sb.toString();
    }

    private StringBuilder buildArtifactListOutput( Set<Artifact> artifacts, boolean outputAbsoluteArtifactFilename,
                                                   boolean theOutputScope, boolean theSort )
    {
        StringBuilder sb = new StringBuilder();
        List<String> artifactStringList = new ArrayList<>();
        for ( Artifact artifact : artifacts )
        {
            MessageBuilder messageBuilder = MessageUtils.buffer();

            messageBuilder.a( "   " );

            if ( theOutputScope )
            {
                messageBuilder.a( artifact.toString() );
            }
            else
            {
                messageBuilder.a( artifact.getId() );
            }

            if ( outputAbsoluteArtifactFilename )
            {
                try
                {
                    // we want to print the absolute file name here
                    String artifactFilename = artifact.getFile().getAbsoluteFile().getPath();

                    messageBuilder.a( ':' ).a( artifactFilename );
                }
                catch ( NullPointerException e )
                {
                    // ignore the null pointer, we'll output a null string
                }
            }

            if ( theOutputScope && artifact.isOptional() )
            {
                messageBuilder.a( " (optional) " );
            }

            // dependencies:collect won't download jars
            if ( artifact.getFile() != null )
            {
                ModuleDescriptor moduleDescriptor = getModuleDescriptor( artifact.getFile() );
                if ( moduleDescriptor != null )
                {
                    messageBuilder.project( " -- module " + moduleDescriptor.name );

                    if ( moduleDescriptor.automatic )
                    {
                        if ( "MANIFEST".equals( moduleDescriptor.moduleNameSource ) )
                        {
                            messageBuilder.strong( " [auto]" );
                        }
                        else
                        {
                            messageBuilder.warning( " (auto)" );
                        }
                    }
                }
            }
            artifactStringList.add( messageBuilder.toString() + System.lineSeparator() );
        }
        if ( theSort )
        {
            Collections.sort( artifactStringList );
        }
        for ( String artifactString : artifactStringList )
        {
            sb.append( artifactString );
        }
        return sb;
    }

    private void extractJars( Set<Artifact> artifacts, boolean outputAbsoluteArtifactFilename,
            boolean theOutputScope, boolean theSort )
    {
        MiniArtifact module = buildMiniArtifact( projectArtifact );
        List<MiniArtifact> minis = artifacts.stream()
            .map( this::buildMiniArtifact )
            .sorted( Comparator.comparing( MiniArtifact::getArtifactId ) )
            .collect( Collectors.toList() );
        module.setZependencies( minis );

        if ( outputType.equalsIgnoreCase( "all" ) || outputType.equalsIgnoreCase( "yaml" ) )
        {
            // write to file(yaml)
            String filenameYaml = prjDir.concat( "/dep.yaml" );
            File fileYaml = new File( filenameYaml );
            String yaml = ( new Yaml() ).dump( module );
            try
            {
                DependencyUtil.write( yaml, fileYaml, appendStatus, null );
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
        }

        if ( outputType.equalsIgnoreCase( "all" ) || outputType.equalsIgnoreCase( "json" ) )
        {
            // write to file(json)
            String fileName = prjDir.concat( "/dep.json" );
            File file = new File( fileName );
            String json = JSONObject.toJSONString( module ) + ",";
            if ( !appendStatus )
            {
                json = "{\"modules\": [" + json;
            }
            try
            {
                DependencyUtil.write( json, file, appendStatus, null );
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
        }

        if ( !appendStatus )
        {
            appendStatus = true;
        }
    }

    private MiniArtifact buildMiniArtifact( Artifact artifact )
    {
        MiniArtifact mini = new MiniArtifact();
        mini.build( artifact );
        return mini;
    }

    private ModuleDescriptor getModuleDescriptor( File artifactFile )
    {
        ModuleDescriptor moduleDescriptor = null;
        try
        {
            // Use Java9 code to get moduleName, don't try to do it better with own implementation
            Class<?> moduleFinderClass = Class.forName( "java.lang.module.ModuleFinder" );

            java.nio.file.Path path = artifactFile.toPath();

            Method ofMethod = moduleFinderClass.getMethod( "of", java.nio.file.Path[].class );
            Object moduleFinderInstance = ofMethod.invoke( null, new Object[] { new java.nio.file.Path[] { path } } );

            Method findAllMethod = moduleFinderClass.getMethod( "findAll" );
            Set<Object> moduleReferences = (Set<Object>) findAllMethod.invoke( moduleFinderInstance );

            // moduleReferences can be empty when referring to target/classes without module-info.class
            if ( !moduleReferences.isEmpty() )
            {
                Object moduleReference = moduleReferences.iterator().next();
                Method descriptorMethod = moduleReference.getClass().getMethod( "descriptor" );
                Object moduleDescriptorInstance = descriptorMethod.invoke( moduleReference );

                Method nameMethod = moduleDescriptorInstance.getClass().getMethod( "name" );
                String name = (String) nameMethod.invoke( moduleDescriptorInstance );

                moduleDescriptor = new ModuleDescriptor();
                moduleDescriptor.name = name;

                Method isAutomaticMethod = moduleDescriptorInstance.getClass().getMethod( "isAutomatic" );
                moduleDescriptor.automatic = (Boolean) isAutomaticMethod.invoke( moduleDescriptorInstance );

                if ( moduleDescriptor.automatic )
                {
                    if ( artifactFile.isFile() )
                    {
                        JarFile jarFile = null;
                        try
                        {
                            jarFile = new JarFile( artifactFile );

                            Manifest manifest = jarFile.getManifest();

                            if ( manifest != null
                                && manifest.getMainAttributes().getValue( "Automatic-Module-Name" ) != null )
                            {
                                moduleDescriptor.moduleNameSource = "MANIFEST";
                            }
                            else
                            {
                                moduleDescriptor.moduleNameSource = "FILENAME";
                            }
                        }
                        catch ( IOException e )
                        {
                            // noop
                        }
                        finally
                        {
                            if ( jarFile != null )
                            {
                                try
                                {
                                    jarFile.close();
                                }
                                catch ( IOException e )
                                {
                                    // noop
                                }
                            }
                        }
                    }
                }
            }
        }
        catch ( ClassNotFoundException | SecurityException | IllegalAccessException | IllegalArgumentException e )
        {
            // do nothing
        }
        catch ( NoSuchMethodException e )
        {
            e.printStackTrace();
        }
        catch ( InvocationTargetException e )
        {
            Throwable cause = e.getCause();
            while ( cause.getCause() != null )
            {
                cause = cause.getCause();
            }
            getLog().info( "Can't extract module name from " + artifactFile.getName() + ": " + cause.getMessage() );
        }
        return moduleDescriptor;
    }

    private class ModuleDescriptor
    {
        String name;

        boolean automatic = true;

        String moduleNameSource;
    }
}
