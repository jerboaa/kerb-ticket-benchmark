package org.jerboaa;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class KrbTicketBenchmark {
	
    @State(Scope.Benchmark)
    public static class ConfigState {
    	private static String CONFIG_ITEM_NAME = "ticketCache";
        private static String KRBLOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";
        
        public ConfigState() {
        	setupConfiguration();
        }

        /**
         * Equivalent of:
         * 
         * {@code
         * 
         * ticketCache {
         * com.sun.security.auth.module.Krb5LoginModule required
         * refreshKrb5Config=false
         * useTicketCache=true
         * doNotPrompt=true
         * useKeyTab=false
         * renewTGT=false
         * isInitiator=false debug=true; };
         * 
         * }
         *
         */
        static class CustomKrbConfig extends Configuration {

            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if (CONFIG_ITEM_NAME.equals(name)) {
                    Map<String, String> options = new HashMap<>();
                    options.put("refreshKrb5Config", Boolean.FALSE.toString());
                    options.put("useTicketCache", Boolean.TRUE.toString());
                    options.put("doNotPrompt", Boolean.TRUE.toString());
                    options.put("useKeyTab", Boolean.FALSE.toString());
                    options.put("isInitiator", Boolean.FALSE.toString());
                    options.put("renewTGT", Boolean.FALSE.toString());
                    options.put("debug", Boolean.FALSE.toString());
                    return new AppConfigurationEntry[] {
                            new AppConfigurationEntry(KRBLOGIN_MODULE,
                                    LoginModuleControlFlag.REQUIRED, options) };
                }
                return null;
            }

        }

        private static void setupConfiguration() {
            Configuration.setConfiguration(new CustomKrbConfig());
        }

        boolean exists() {
            LoginContext lc = null;
            try {
                lc = new LoginContext(CONFIG_ITEM_NAME, new CallbackHandler() {

                    @Override
                    public void handle(Callback[] callbacks)
                            throws IOException, UnsupportedCallbackException {
                        // config has doNotPrompt, so it should never happen
                        throw new RuntimeException("Should not happen!");
                    }

                });
                lc.login();
            } catch (LoginException e) {
                return false;
            }
            Subject sub = lc.getSubject();
            return sub != null;
        }
    }
    
    @State(Scope.Benchmark)
    public static class InternalJDKState {
    	
    	@SuppressWarnings("restriction")
		public boolean exists() {
			try {
				sun.security.krb5.Credentials credentials = sun.security.krb5.Credentials.acquireTGTFromCache(null,
						null);
				return credentials != null;
			} catch (Exception ex) {
				return false;
			}
    	}
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public boolean baseLine() {
        // Intentionally do nothing, so as to measure the infra overhead
    	return false;
    }
    
    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public boolean internalJDKApi(InternalJDKState state) {
    	return state.exists(); // the benchmark method
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public boolean jdk17CompatibleApi(ConfigState state) {
    	return state.exists(); // the actual benchmark method
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(KrbTicketBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
