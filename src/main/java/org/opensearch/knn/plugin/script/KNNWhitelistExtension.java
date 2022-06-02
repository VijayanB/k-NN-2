/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.plugin.script;

import org.opensearch.painless.spi.PainlessExtension;
import org.opensearch.painless.spi.Whitelist;
import org.opensearch.painless.spi.WhitelistLoader;
import org.opensearch.script.ScoreScript;
import org.opensearch.script.ScriptContext;
import org.opensearch.script.ScriptedMetricAggContexts;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Map.entry;

public class KNNWhitelistExtension implements PainlessExtension {

    private static final Whitelist WHITELIST = WhitelistLoader.loadFromResourceFiles(KNNWhitelistExtension.class, "knn_whitelist.txt");

    @Override
    public Map<ScriptContext<?>, List<Whitelist>> getContextWhitelists() {
        List<Whitelist> whitelist = singletonList(WHITELIST);
        return Map.ofEntries(
            entry(ScoreScript.CONTEXT, whitelist),
            entry(ScriptedMetricAggContexts.InitScript.CONTEXT, whitelist),
            entry(ScriptedMetricAggContexts.MapScript.CONTEXT, whitelist),
            entry(ScriptedMetricAggContexts.CombineScript.CONTEXT, whitelist),
            entry(ScriptedMetricAggContexts.ReduceScript.CONTEXT, whitelist)
        );
    }
}
