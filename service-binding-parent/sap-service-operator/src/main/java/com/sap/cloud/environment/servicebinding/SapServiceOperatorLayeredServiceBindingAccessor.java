/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */

package com.sap.cloud.environment.servicebinding;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sap.cloud.environment.api.ServiceBinding;
import com.sap.cloud.environment.api.ServiceBindingAccessor;
import com.sap.cloud.environment.api.exception.ServiceBindingAccessException;

public class SapServiceOperatorLayeredServiceBindingAccessor implements ServiceBindingAccessor
{
    @Nonnull
    public static final Path DEFAULT_ROOT_PATH = Paths.get("/etc/secrets/sapbtp");
    @Nonnull
    public static final Collection<LayeredParsingStrategy> DEFAULT_PARSING_STRATEGIES = Collections.unmodifiableCollection(
            Arrays.asList(LayeredSecretRootKeyParsingStrategy.newDefault(),
                          LayeredSecretKeyParsingStrategy.newDefault(),
                          LayeredDataParsingStrategy.newDefault()));
    @Nonnull
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    @Nonnull
    private final Path rootPath;
    @Nonnull
    private final Collection<LayeredParsingStrategy> parsingStrategies;

    public SapServiceOperatorLayeredServiceBindingAccessor()
    {
        this(DEFAULT_ROOT_PATH, DEFAULT_PARSING_STRATEGIES);
    }

    public SapServiceOperatorLayeredServiceBindingAccessor( @Nonnull final Path rootPath,
                                                            @Nonnull final Collection<LayeredParsingStrategy> parsingStrategies )
    {
        this.rootPath = rootPath;
        this.parsingStrategies = parsingStrategies;
    }

    @Nonnull
    @Override
    public List<ServiceBinding> getServiceBindings()
    {
        try (final Stream<Path> files = Files.list(rootPath)) {
            return files.filter(Files::isDirectory).flatMap(this::parseServiceBindings).collect(Collectors.toList());
        } catch (final SecurityException | IOException e) {
            throw new ServiceBindingAccessException("Unable to access service binding files.", e);
        }
    }

    @Nonnull
    private Stream<ServiceBinding> parseServiceBindings( @Nonnull final Path servicePath )
    {
        try {
            return Files.list(servicePath)
                        .filter(Files::isDirectory)
                        .map(bindingPath -> parsingStrategies.stream()
                                                             .map(strategy -> applyStrategy(strategy,
                                                                                            servicePath,
                                                                                            bindingPath))
                                                             .filter(Optional::isPresent)
                                                             .findFirst()
                                                             .orElse(Optional.empty()))
                        .filter(Optional::isPresent)
                        .map(Optional::get);
        } catch (final IOException e) {
            throw new ServiceBindingAccessException(String.format("Unable to access service binding files in '%s'.",
                                                                  servicePath), e);
        }
    }

    @Nonnull
    private Optional<ServiceBinding> applyStrategy( @Nonnull final LayeredParsingStrategy strategy,
                                                    @Nonnull final Path servicePath,
                                                    @Nonnull final Path bindingPath )
    {
        try {
            return strategy.parse(servicePath.getFileName().toString(),
                                  bindingPath.getFileName().toString(),
                                  bindingPath);
        } catch (final IOException e) {
            return Optional.empty();
        }
    }
}