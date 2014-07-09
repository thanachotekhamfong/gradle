/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.plugins.PluginAware;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.Actions;
import org.gradle.util.GUtil;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultObjectConfigurationAction implements ObjectConfigurationAction {

    private final FileResolver resolver;
    private final ScriptPluginFactory configurerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final Set<Object> targets = new LinkedHashSet<Object>();
    private final Set<Runnable> actions = new LinkedHashSet<Runnable>();
    private final Action<? super PluginApplication> postApplyAction;
    private final ClassLoaderScope classLoaderScope;
    private final Object[] defaultTargets;

    public DefaultObjectConfigurationAction(
            FileResolver resolver,
            ScriptPluginFactory configurerFactory,
            ScriptHandlerFactory scriptHandlerFactory,
            ClassLoaderScope classLoaderScope,
            Object... defaultTargets
    ) {
        this(resolver, configurerFactory, scriptHandlerFactory, Actions.doNothing(), classLoaderScope, defaultTargets);
    }

    public DefaultObjectConfigurationAction(FileResolver resolver, ScriptPluginFactory configurerFactory, ScriptHandlerFactory scriptHandlerFactory, Action<? super PluginApplication> postApplyAction, ClassLoaderScope classLoaderScope, Object... defaultTargets) {
        this.resolver = resolver;
        this.configurerFactory = configurerFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.postApplyAction = postApplyAction;
        this.classLoaderScope = classLoaderScope;
        this.defaultTargets = defaultTargets;
    }

    public ObjectConfigurationAction to(Object... targets) {
        GUtil.flatten(targets, this.targets);
        return this;
    }

    public ObjectConfigurationAction from(final Object script) {
        actions.add(new Runnable() {
            public void run() {
                applyScript(script);
            }
        });
        return this;
    }

    public ObjectConfigurationAction plugin(final Class<? extends Plugin> pluginClass) {
        actions.add(new Runnable() {
            public void run() {
                applyPlugin(pluginClass);
            }
        });
        return this;
    }

    public ObjectConfigurationAction plugin(final String pluginId) {
        actions.add(new Runnable() {
            public void run() {
                applyPlugin(pluginId);
            }
        });
        return this;
    }

    private void applyScript(Object script) {
        URI scriptUri = resolver.resolveUri(script);
        UriScriptSource scriptSource = new UriScriptSource("script", scriptUri);
        ClassLoaderScope classLoaderScopeChild = classLoaderScope.createChild();
        ScriptHandler scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScopeChild);
        ScriptPlugin configurer = configurerFactory.create(scriptSource, scriptHandler, classLoaderScopeChild, classLoaderScope, "buildscript", DefaultScript.class, false);
        for (Object target : targets) {
            configurer.apply(target);
        }
    }

    private void applyPlugin(Class<? extends Plugin> pluginClass) {
        for (Object target : targets) {
            if (target instanceof PluginAware) {
                PluginAware pluginAware = (PluginAware) target;
                try {
                    Plugin plugin = pluginAware.getPlugins().apply(pluginClass);
                    postApplyAction.execute(new PluginApplication(plugin, pluginAware));
                } catch (Exception e) {
                    throw new PluginApplicationException("class '" + pluginClass.getName() + "'", e);
                }
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin of class '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginClass.getName(), target.toString(), target.getClass().getName()));
            }
        }
    }

    private void applyPlugin(String pluginId) {
        for (Object target : targets) {
            if (target instanceof PluginAware) {
                PluginAware pluginAware = (PluginAware) target;
                try {
                    Plugin plugin = pluginAware.getPlugins().apply(pluginId);
                    postApplyAction.execute(new PluginApplication(plugin, pluginAware));
                } catch (Exception e) {
                    throw new PluginApplicationException("id '" + pluginId + "'", e);
                }
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin with id '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginId, target.toString(), target.getClass().getName()));
            }
        }
    }

    public void execute() {
        if (targets.isEmpty()) {
            to(defaultTargets);
        }

        for (Runnable action : actions) {
            action.run();
        }
    }
}
