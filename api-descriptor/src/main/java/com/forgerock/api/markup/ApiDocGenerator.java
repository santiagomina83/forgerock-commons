/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package com.forgerock.api.markup;

import static com.forgerock.api.markup.asciidoc.AsciiDoc.asciiDoc;
import static com.forgerock.api.markup.asciidoc.AsciiDoc.normalizeName;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.forgerock.api.markup.asciidoc.AsciiDoc;
import com.forgerock.api.models.ApiDescription;
import com.forgerock.api.models.Definitions;
import com.forgerock.api.models.Errors;
import com.forgerock.api.models.Paths;
import com.forgerock.api.models.Resource;
import com.forgerock.api.models.VersionedPath;

/**
 * Generates static AsciiDoc documentation for CREST API Descriptors.
 */
public class ApiDocGenerator {

    private final Path outputDirPath;

    /**
     * Constructor that sets the root output directory for AsciiDoc files, which will be created if it does not exist.
     *
     * @param outputDirPath Root output directory
     */
    public ApiDocGenerator(final Path outputDirPath) {
        this.outputDirPath = checkNotNull(outputDirPath, "outputDirPath required");
    }

    /**
     * Generates AsciiDoc documentation for a CREST API Descriptor.
     *
     * @param apiDescription API Description
     */
    @SuppressWarnings("unchecked")
    public void execute(final ApiDescription apiDescription) {
        final String namespace = apiDescription.getId();
        try {
            // output paths with or without versions
            String pathsFilename = null;
            if (apiDescription.getPaths() != null) {
                try {
                    pathsFilename = outputPaths((Paths<Resource>) apiDescription.getPaths(), namespace);
                } catch (ClassCastException e1) {
                    try {
                        pathsFilename = outputVersionedPaths((Paths<VersionedPath>) apiDescription.getPaths(),
                                namespace);
                    } catch (ClassCastException e2) {
                        throw new ApiDocGeneratorException(
                                "Unsupported Paths type: " + apiDescription.getPaths().getClass().getName());
                    }
                }
            }

            final String definitionsFilename = outputDefinitions(apiDescription.getDefinitions(), namespace);
            final String errorsFilename = outputErrors(apiDescription.getErrors(), namespace);
            outputRoot(apiDescription, pathsFilename, definitionsFilename, errorsFilename, namespace);
        } catch (IOException e) {
            throw new ApiDocGeneratorException("Unable to output doc file", e);
        }
    }

    /**
     * Outputs a top-level AsciiDoc file that imports all other second-level files generated by this class.
     *
     * @param apiDescription API Description
     * @param pathsFilename Paths file-path suitable for AsciiDoc import-statement
     * @param definitionsFilename Definitions-file path suitable for AsciiDoc import-statement
     * @param errorsFilename Errors-file path suitable for AsciiDoc import-statement
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputRoot(final ApiDescription apiDescription, final String pathsFilename,
            final String definitionsFilename, final String errorsFilename,
            final String parentNamespace) throws IOException {
        final String namespace = normalizeName(parentNamespace, "index");

        final AsciiDoc pathsDoc = asciiDoc()
                .documentTitle("API Descriptor")
                .sectionTitle1(asciiDoc().rawText("ID: ").mono(apiDescription.getId()).toString())
                .newline()
                .rawText(apiDescription.getDescription())
                .newline()
                .newline();

        if (pathsFilename != null) {
            pathsDoc.include(pathsFilename);
        }
        if (definitionsFilename != null) {
            pathsDoc.include(definitionsFilename);
        }
        if (errorsFilename != null) {
            pathsDoc.include(errorsFilename);
        }

        final String filename = namespace + ".adoc";
        pathsDoc.toFile(outputDirPath, filename);
        return filename;
    }

    /**
     * Outputs an AsciiDoc file for each path, and another file that imports each path.
     *
     * @param paths Paths
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputPaths(final Paths<Resource> paths, final String parentNamespace) throws IOException {
        final String allPathsDocNamespace = normalizeName(parentNamespace, "paths");
        final AsciiDoc allPathsDoc = asciiDoc()
                .sectionTitle1("Paths");
        final List<String> pathNames = new ArrayList<>(paths.getNames());
        Collections.sort(pathNames);
        for (final String pathName : pathNames) {
            // path
            final String pathDocNamespace = normalizeName(allPathsDocNamespace, pathName);
            final AsciiDoc pathDoc = asciiDoc()
                    .sectionTitle2(asciiDoc().mono(pathName).toString());

            // resource
            final String resourceImport = outputResource(paths.get(pathName), 2, pathDocNamespace);
            pathDoc.include(resourceImport);

            // output path-file
            final String pathDocFilename = pathDocNamespace + ".adoc";
            pathDoc.toFile(outputDirPath, pathDocFilename);

            // include path-file
            allPathsDoc.include(pathDocFilename);
        }

        // output all-paths-file
        final String filename = allPathsDocNamespace + ".adoc";
        allPathsDoc.toFile(outputDirPath, filename);
        return filename;
    }

    /**
     * Outputs an AsciiDoc file for each path, which imports a file for each version under that path, and another
     * file that imports each path.
     *
     * @param paths Versioned paths
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputVersionedPaths(final Paths<VersionedPath> paths, final String parentNamespace)
            throws IOException {
        final String allPathsDocNamespace = normalizeName(parentNamespace, "paths");
        final AsciiDoc allPathsDoc = asciiDoc()
                .sectionTitle1("Paths");

        final List<String> pathNames = new ArrayList<>(paths.getNames());
        Collections.sort(pathNames);
        for (final String pathName : pathNames) {
            // path
            final String pathDocNamespace = normalizeName(allPathsDocNamespace, pathName);
            final AsciiDoc pathDoc = asciiDoc()
                    .sectionTitle2(asciiDoc().mono(pathName).toString());

            final VersionedPath versionedPath = paths.get(pathName);
            final List<String> versions = new ArrayList<>(versionedPath.getVersions());
            Collections.sort(versions);
            for (final String versionName : versions) {
                // version
                final String versionDocNamespace = normalizeName(pathDocNamespace, versionName);
                final AsciiDoc versionDoc = asciiDoc()
                        .sectionTitle3(asciiDoc().mono(versionName).toString());

                // resource
                final String resourceImport = outputResource(versionedPath.get(versionName), 3, versionDocNamespace);
                versionDoc.include(resourceImport);

                // output version-file
                final String versionDocFilename = versionDocNamespace + ".adoc";
                versionDoc.toFile(outputDirPath, versionDocFilename);

                // include version-file
                pathDoc.include(versionDocFilename);
            }

            // output path-file
            final String pathDocFilename = pathDocNamespace + ".adoc";
            pathDoc.toFile(outputDirPath, pathDocFilename);

            // include path-file
            allPathsDoc.include(pathDocFilename);
        }

        // output all-paths-file
        final String filename = allPathsDocNamespace + ".adoc";
        allPathsDoc.toFile(outputDirPath, filename);
        return filename;
    }

    /**
     * Outputs an AsciiDoc file for the resource and each operation, and a file that imports each of those files.
     *
     * @param resource Resource
     * @param parentSectionLevel Parent's section-level
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputResource(final Resource resource, final int parentSectionLevel, final String parentNamespace)
            throws IOException {
        final String namespace = normalizeName(parentNamespace, "resource");
        final AsciiDoc resourceDoc = asciiDoc();
        final int sectionLevel = parentSectionLevel + 1;

        // TODO each of the following should actually output to a separate file

        if (resource.getResourceSchema() != null) {
            resourceDoc.sectionTitle("Resource Schema", sectionLevel);
            // TODO
        }

        if (resource.getCreate() != null) {
            resourceDoc.sectionTitle("Create", sectionLevel);
            // TODO
        }

        if (resource.getRead() != null) {
            resourceDoc.sectionTitle("Read", sectionLevel);
            // TODO
        }

        if (resource.getUpdate() != null) {
            resourceDoc.sectionTitle("Update", sectionLevel);
            // TODO
        }

        if (resource.getDelete() != null) {
            resourceDoc.sectionTitle("Delete", sectionLevel);
            // TODO
        }

        if (resource.getPatch() != null) {
            resourceDoc.sectionTitle("Patch", sectionLevel);
            // TODO
        }

        if (resource.getActions() != null && resource.getActions().length != 0) {
            resourceDoc.sectionTitle("Actions", sectionLevel);
            // TODO
        }

        if (resource.getQueries() != null && resource.getQueries().length != 0) {
            resourceDoc.sectionTitle("Queries", sectionLevel);
            // TODO
        }

        final String filename = namespace + ".adoc";
        resourceDoc.toFile(outputDirPath, filename);
        return filename;
    }

    /**
     * Outputs an AsciiDoc file for each schema definition, and a file that imports all schema definitions.
     *
     * @param definitions Schema definitions
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputDefinitions(final Definitions definitions, final String parentNamespace) throws IOException {
        if (definitions == null) {
            return null;
        }
        // TODO
        return null;
    }

    /**
     * Outputs an AsciiDoc file containing all defined errors.
     *
     * @param errors Errors
     * @param parentNamespace Parent namespace
     * @return File path suitable for AsciiDoc import-statement
     * @throws IOException Unable to output AsciiDoc file
     */
    private String outputErrors(final Errors errors, final String parentNamespace) throws IOException {
        if (errors == null) {
            return null;
        }
        // TODO
        return null;
    }

}
