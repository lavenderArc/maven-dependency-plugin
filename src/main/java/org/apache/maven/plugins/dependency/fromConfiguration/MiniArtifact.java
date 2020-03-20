package org.apache.maven.plugins.dependency.fromConfiguration;

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

import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;

/**
 * 功能描述
 *
 * @since 2020-03-20
 */
public class MiniArtifact
{
    private String groupId;

    private String artifactId;

    private String version;

    private String scope;

    private List<MiniArtifact> zependencies;

    private String jarName;

    public void build( Artifact artifact )
    {
        this.groupId = artifact.getGroupId();
        this.artifactId = artifact.getArtifactId();
        this.version = artifact.getVersion();
        this.scope = artifact.getScope();
        this.setZependencies( null );
        this.jarName = String.format( Locale.ENGLISH, "%s-%s.jar", artifactId, version );
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion( String version )
    {
        this.version = version;
    }

    public String getScope()
    {
        return scope;
    }

    public void setScope( String scope )
    {
        this.scope = scope;
    }

    public List<MiniArtifact> getZependencies()
    {
        return zependencies;
    }

    public void setZependencies( List<MiniArtifact> dependencies )
    {
        this.zependencies = dependencies;
    }

    public String getJarName()
    {
        return jarName;
    }

    public void setJarName( String jarName )
    {
        this.jarName = jarName;
    }
}
