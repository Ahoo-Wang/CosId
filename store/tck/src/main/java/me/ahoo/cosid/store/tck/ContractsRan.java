/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.store.tck;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Checks from the JUnit XML reports of a backend that its contract cases really ran (roadmap R2: a CI job that
 * silently skipped its backend must fail). For each contract, some test class extending it must have reported every
 * case of the contract as passed; a case marked {@link OptionalCapability} may be skipped.
 *
 * <p>Run it on the test runtime classpath of the backend, after its tests:
 * {@code ContractsRan <report dir> [contract class ...]}; the contracts default to {@link RangeStoreContract} and
 * {@link SlotLeaseStoreContract}. It exits with status 1 and lists the problems when a case did not run.
 */
public final class ContractsRan {
    private ContractsRan() {
    }

    /**
     * Checks the reports in {@code args[0]} against the contracts named by the other arguments.
     *
     * @param args the report directory, then the contract classes
     * @throws Exception if a report or a class cannot be read
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ContractsRan <junit xml report dir> [contract class ...]");
            System.exit(2);
        }
        List<Class<?>> contracts = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            contracts.add(Class.forName(args[i]));
        }
        if (contracts.isEmpty()) {
            contracts = List.of(RangeStoreContract.class, SlotLeaseStoreContract.class);
        }
        List<String> problems = check(Path.of(args[0]), contracts, Thread.currentThread().getContextClassLoader());
        if (!problems.isEmpty()) {
            System.err.println("Contract cases that did not run in " + args[0] + ":");
            problems.forEach(problem -> System.err.println("  " + problem));
            System.exit(1);
        }
        System.out.println("Every case of " + contracts.stream().map(Class::getSimpleName).toList() + " ran in " + args[0]);
    }

    /**
     * Lists the contract cases that no report shows as run.
     *
     * @param reportDir   the directory of the {@code TEST-*.xml} reports
     * @param contracts   the contract classes
     * @param classLoader loads the test classes the reports name
     * @return the problems; empty when every case ran
     * @throws IOException if a report cannot be read
     */
    public static List<String> check(Path reportDir, List<Class<?>> contracts, ClassLoader classLoader) throws IOException {
        Map<String, Map<String, Outcome>> reports = readReports(reportDir);
        List<String> problems = new ArrayList<>();
        for (Class<?> contract : contracts) {
            List<Map<String, Outcome>> implementations = new ArrayList<>();
            for (Map.Entry<String, Map<String, Outcome>> report : reports.entrySet()) {
                if (extendsContract(report.getKey(), contract, classLoader)) {
                    implementations.add(report.getValue());
                }
            }
            if (implementations.isEmpty()) {
                problems.add(contract.getSimpleName() + ": no test class extending it reported");
                continue;
            }
            for (Method method : contract.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Test.class)) {
                    continue;
                }
                boolean optional = method.isAnnotationPresent(OptionalCapability.class);
                boolean ran = implementations.stream().map(cases -> cases.get(method.getName()))
                    .anyMatch(outcome -> outcome == Outcome.PASSED || (optional && outcome == Outcome.SKIPPED));
                if (!ran) {
                    problems.add(contract.getSimpleName() + "." + method.getName() + ": not reported as passed");
                }
            }
        }
        return problems;
    }

    private static boolean extendsContract(String className, Class<?> contract, ClassLoader classLoader) {
        try {
            return contract.isAssignableFrom(Class.forName(className, false, classLoader));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** The outcome of each case, by test class then method name; a case reported twice (a retry) keeps its best outcome. */
    private static Map<String, Map<String, Outcome>> readReports(Path reportDir) throws IOException {
        Map<String, Map<String, Outcome>> reports = new TreeMap<>();
        if (!Files.isDirectory(reportDir)) {
            return reports;
        }
        DocumentBuilder builder = newBuilder();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(reportDir, "TEST-*.xml")) {
            for (Path file : files) {
                Document document;
                try (InputStream in = Files.newInputStream(file)) {
                    document = builder.parse(in);
                } catch (SAXException e) {
                    throw new IOException("Unreadable report " + file, e);
                }
                NodeList cases = document.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    Element testCase = (Element) cases.item(i);
                    String name = testCase.getAttribute("name");
                    int paren = name.indexOf('(');
                    String method = paren < 0 ? name : name.substring(0, paren);
                    reports.computeIfAbsent(testCase.getAttribute("classname"), k -> new TreeMap<>())
                        .merge(method, outcomeOf(testCase), (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
                }
            }
        }
        return reports;
    }

    private static Outcome outcomeOf(Element testCase) {
        if (testCase.getElementsByTagName("failure").getLength() > 0 || testCase.getElementsByTagName("error").getLength() > 0) {
            return Outcome.FAILED;
        }
        if (testCase.getElementsByTagName("skipped").getLength() > 0) {
            return Outcome.SKIPPED;
        }
        return Outcome.PASSED;
    }

    private static DocumentBuilder newBuilder() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException("No XML parser", e);
        }
    }

    private enum Outcome {
        PASSED,
        SKIPPED,
        FAILED
    }
}
