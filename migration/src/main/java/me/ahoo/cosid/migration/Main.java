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

package me.ahoo.cosid.migration;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * {@code java -jar cosid-migration.jar <command> [options]} (09 §3). Exit codes: 0 done, 1 a check failed or the
 * command aborted, 2 a usage error.
 */
public final class Main {
    static final String USAGE = """
        Usage: java -jar cosid-migration.jar <command> [options]

          plan    --from <4.x store> --to <5.0 store> --namespace <ns> [--output plan.json] [--layout-config <4.x yaml>]
                  [--machine-from <4.x machine store>] [--machine-to <5.0 slot store>]
                  [--target-config <5.0 yaml>] [--slot-bits 10] [--tolerance 1s] [--source-skew 10s]
                  [--slot-offset <n>]   (the shared cosid.generator.namespace only: default 2^(4.x cosid.generator.machine-bit),
                                         the 4.x hard cap, when the slots fit above it; otherwise required, and 4.x must
                                         not hand out machine ids >= <n>; 0 migrates it at once, every service stopped)
                  [--rename <4.x name>=<5.0 name>]... [--group-pattern <generator>=<4.x pattern>]...
                  [--from-option k=v]... [--to-option k=v]...
          apply   --plan plan.json --confirm-stopped [--observe 2m] [--gap 0] [--source-skew 10s] [--no-seal-source]
                  [--seal-gap 1099511627776] [--result apply-result.json] [--layout-config ..] [--target-config ..]
                  [--allow-unmigrated <4.x key>]...
                  [--from-option k=v]... [--to-option k=v]...
          verify  --plan plan.json --result apply-result.json [--from-option k=v]... [--to-option k=v]...
          config  --input application.yaml --result apply-result.json --output application-5.yaml [--zone Asia/Shanghai]
                  [--allow-new <generator>]...
          purge   --to <5.0 store> --namespace <ns> --before <period> (--confirm-stopped | --dry-run) [--to-option k=v]...

        Stores: jdbc:<url> (options user, password), redis://host:port (option cluster=true), mongodb://host/
        (option database, default cosid_db), zookeeper://host:port[/chroot]. Passwords: --from-option password=..,
        or COSID_MIGRATION_FROM_PASSWORD / COSID_MIGRATION_TO_PASSWORD. See the README of the migration module.
        """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs a command.
     *
     * @param args the arguments
     * @param out  the report
     * @param err  errors
     * @return the exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            out.print(USAGE);
            return args.length == 0 ? 2 : 0;
        }
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        Migration migration = new Migration(out);
        try {
            switch (args[0]) {
                case "plan" -> {
                    Args options = Args.parse(rest, Set.of(), Set.of("from", "to", "machine-from", "machine-to", "namespace", "output", "layout-config",
                        "target-config", "slot-bits", "tolerance", "source-skew", "rename", "group-pattern", "from-option", "to-option",
                        "machine-from-option", "machine-to-option", "slot-offset"));
                    String bits = options.get("slot-bits");
                    String slotOffset = options.get("slot-offset");
                    String machineFrom = options.get("machine-from");
                    String machineTo = options.get("machine-to");
                    migration.plan(new Migration.PlanOptions(
                        StoreLocation.parse(options.required("from"), options.pairs("from-option")).withEnvironmentPassword("FROM"),
                        StoreLocation.parse(options.required("to"), options.pairs("to-option")).withEnvironmentPassword("TO"),
                        machineFrom == null ? null : StoreLocation.parse(machineFrom, options.pairs("machine-from-option")).withEnvironmentPassword("MACHINE_FROM"),
                        machineTo == null ? null : StoreLocation.parse(machineTo, options.pairs("machine-to-option")).withEnvironmentPassword("MACHINE_TO"),
                        options.required("namespace"), options.path("layout-config"), options.path("target-config"),
                        bits == null ? null : Integer.parseInt(bits), options.duration("tolerance", Duration.ofSeconds(1)),
                        options.duration("source-skew", Migration.DEFAULT_SOURCE_SKEW), options.pairs("rename"), options.pairs("group-pattern"),
                        options.path("output", "plan.json"), slotOffset == null ? null : Integer.parseInt(slotOffset)));
                    return 0;
                }
                case "apply" -> {
                    Args options = Args.parse(rest, Set.of("confirm-stopped", "no-seal-source"), Set.of("plan", "confirm-stopped", "observe", "gap",
                        "source-skew", "no-seal-source", "seal-gap", "result", "from-option", "to-option", "layout-config", "target-config", "allow-unmigrated",
                        "machine-from-option", "machine-to-option"));
                    migration.apply(new Migration.ApplyOptions(options.path("plan", "plan.json"), options.flag("confirm-stopped"),
                        options.duration("observe", Migration.DEFAULT_OBSERVE), options.number("gap", 0),
                        options.duration("source-skew", Migration.DEFAULT_SOURCE_SKEW), !options.flag("no-seal-source"),
                        options.number("seal-gap", Migration.DEFAULT_SEAL_GAP), options.path("result", "apply-result.json"),
                        options.pairs("from-option"), options.pairs("to-option"), options.path("layout-config"), options.path("target-config"),
                        Set.copyOf(options.all("allow-unmigrated")), options.pairs("machine-from-option"), options.pairs("machine-to-option")));
                    return 0;
                }
                case "verify" -> {
                    Args options = Args.parse(rest, Set.of(), Set.of("plan", "result", "from-option", "to-option", "machine-from-option", "machine-to-option"));
                    List<String> problems = migration.verify(new Migration.VerifyOptions(options.path("plan", "plan.json"),
                        options.path("result", "apply-result.json"), options.pairs("from-option"), options.pairs("to-option"),
                        options.pairs("machine-from-option"), options.pairs("machine-to-option")));
                    return problems.isEmpty() ? 0 : 1;
                }
                case "config" -> {
                    Args options = Args.parse(rest, Set.of(), Set.of("input", "result", "output", "zone", "allow-new"));
                    String zone = options.get("zone");
                    migration.config(new Migration.ConfigOptions(Path.of(options.required("input")), options.path("result"),
                        Path.of(options.required("output")), zone == null ? null : ZoneId.of(zone), Set.copyOf(options.all("allow-new"))));
                    return 0;
                }
                case "purge" -> {
                    Args options = Args.parse(rest, Set.of("confirm-stopped", "dry-run"), Set.of("to", "namespace", "before", "confirm-stopped", "dry-run", "to-option"));
                    migration.purge(new Migration.PurgeOptions(
                        StoreLocation.parse(options.required("to"), options.pairs("to-option")).withEnvironmentPassword("TO"),
                        options.required("namespace"), options.required("before"), options.flag("confirm-stopped"), options.flag("dry-run")));
                    return 0;
                }
                default -> {
                    err.println("Unknown command [" + args[0] + "]");
                    err.print(USAGE);
                    return 2;
                }
            }
        } catch (IllegalArgumentException e) {
            err.println("cosid-migration " + args[0] + ": " + e.getMessage());
            return 2;
        } catch (MigrationAbortedException e) {
            err.println("cosid-migration " + args[0] + " aborted: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("cosid-migration " + args[0] + " failed: " + e);
            return 1;
        }
    }
}
