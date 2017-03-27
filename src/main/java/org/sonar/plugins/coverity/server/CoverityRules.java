/*
 * Coverity Sonar Plugin
 * Copyright (c) 2017 Coverity, Inc
 * support@coverity.com
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 */

package org.sonar.plugins.coverity.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ExtensionPoint;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonar.plugins.coverity.CoverityPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sonar.plugins.coverity.util.CoverityUtil.getValue;

/* From Sonarqube-4.3+ the interface RulesDefinition replaces the (previously deprecated and currently dropped) RulesRepository.
 * This class loads rules into the server by means of an XmlLoader. However we still need to activate these rules under
 * a profile and then again in CoveritySensor.
 */
@ServerSide
@ExtensionPoint
public class CoverityRules implements RulesDefinition {

    private RulesDefinitionXmlLoader xmlLoader = new RulesDefinitionXmlLoader();
    private static final Logger LOG = LoggerFactory.getLogger(CoverityRules.class);

    public CoverityRules(RulesDefinitionXmlLoader xmlLoader) {
        this.xmlLoader = xmlLoader;
    }

    Map<String, NodeList> mapOfNodeLists = new HashMap<String, NodeList>();

    NodeList javaNodes;
    NodeList cppNodes;
    NodeList csNodes;
    NodeList jsNodes;
    NodeList pythonNodes;
    NodeList phpNodes;

    static List<String> languages = new ArrayList<String>();
    static{
        languages.add("java");
        languages.add("cs");
        languages.add("js");
        languages.add("py");
        languages.add("php");
        languages.add(CppLanguage.KEY);
    }

    public static Map<String, org.sonar.api.rules.Rule> javaRulesToBeActivated = new HashMap<String, org.sonar.api.rules.Rule>();
    public static Map<String, org.sonar.api.rules.Rule> csRulesToBeActivated = new HashMap<String, org.sonar.api.rules.Rule>();
    public static Map<String, org.sonar.api.rules.Rule> jsRulesToBeActivated = new HashMap<>();
    public static Map<String, org.sonar.api.rules.Rule> pythonRulesToBeActivated = new HashMap<>();
    public static Map<String, org.sonar.api.rules.Rule> phpulesToBeActivated = new HashMap<>();
    public static Map<String, org.sonar.api.rules.Rule> cppRulesToBeActivated = new HashMap<String, org.sonar.api.rules.Rule>();

    public static Map<String, Map<String, org.sonar.api.rules.Rule>> getMapOfRuleMaps() {
        return mapOfRuleMaps;
    }

    public static Map<String, Map<String, org.sonar.api.rules.Rule>> mapOfRuleMaps = new HashMap<String, Map<String, org.sonar.api.rules.Rule>>();

    static {
        mapOfRuleMaps.put("java", javaRulesToBeActivated);
        mapOfRuleMaps.put("cs", csRulesToBeActivated);
        mapOfRuleMaps.put("js", jsRulesToBeActivated);
        mapOfRuleMaps.put("py", pythonRulesToBeActivated);
        mapOfRuleMaps.put("php", phpulesToBeActivated);
        mapOfRuleMaps.put(CppLanguage.KEY, cppRulesToBeActivated);
    }

    public CoverityRules() {
    }

    /* The interface RulesDefinition provides a default parser: "XmlLoader". However, XmlLoader stores rules as
    *  "NewRules" a class that does not provides getters for certain fields such as severity. We need to access these
    *  fields later on when activating rules in CoverityProfiles. So in order to have more control over our rules we
    *  define "InternalRule.class" and we complete its fields by doing a parsing by ourselves. This is the propose of
    *  "parseRules()".
    * */
    public Map<String, Map<String, org.sonar.api.rules.Rule>> parseRules(){

        for(String language : languages){

            String fileDir = "/org/sonar/plugins/coverity/server/coverity-" + language + ".xml";
            InputStream in = getClass().getResourceAsStream(fileDir);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = null;
            Document doc = null;
            try {
                dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(in);
            } catch (ParserConfigurationException e) {
                LOG.error("Error parsing rules." + e.getCause());
            }
             catch (SAXException e) {
                 LOG.error("Error parsing rules." + e.getCause());
             } catch (IOException e) {
                LOG.error("Error parsing rules." + e.getCause());
            }
            doc.getDocumentElement().normalize();

            NodeList nodes = doc.getElementsByTagName("rule");

            if(language.equals("java")){
                javaNodes = nodes;
                mapOfNodeLists.put("java", javaNodes);
            } else if (language.equals(CppLanguage.KEY)){
                cppNodes = nodes;
                mapOfNodeLists.put(CppLanguage.KEY, cppNodes);
            } else if (language.equals("cs")){
                csNodes = nodes;
                mapOfNodeLists.put("cs", csNodes);
            } else if (language.equals("js")){
                jsNodes = nodes;
                mapOfNodeLists.put("js", jsNodes);
            } else if (language.equals("py")){
                pythonNodes = nodes;
                mapOfNodeLists.put("py", pythonNodes);
            } else if (language.equals("php")){
                phpNodes = nodes;
                mapOfNodeLists.put("php", phpNodes);
            }

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);

                String key = "";
                String name = "";
                String severity = "";
                String description = "";

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    key = getValue("key", element);
                    name = getValue("name", element);
                    severity = getValue("severity", element);
                    description = getValue("description", element);
                }

                org.sonar.api.rules.Rule covRule = org.sonar.api.rules.Rule.create("coverity-" + language, key);
                covRule.setName(name);
                covRule.setLanguage(language);
                covRule.setDescription(description);
                covRule.setSeverity(RulePriority.valueOf(severity));

                mapOfRuleMaps.get(language).put(key, covRule);
            }
        }

        return mapOfRuleMaps;
    }

    @Override
    public void define(Context context) {
        parseRules();

        for(String language : languages){
            NewRepository repository = context.createRepository(CoverityPlugin.REPOSITORY_KEY + "-" + language, language).setName(language + "-repository");
            String fileDir = "coverity-" + language + ".xml";
            InputStream in = getClass().getResourceAsStream(fileDir);
            xmlLoader.load(repository, in, "UTF-8");
            repository.done();
        }

    }
}


