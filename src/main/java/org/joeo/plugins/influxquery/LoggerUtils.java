/*
 * The MIT License
 *
 * Copyright (c) 2018 Joe Offenberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.joeo.plugins.influxquery;

import java.io.PrintStream;

/**
 * Stupid class to prefix log messages with a level
 * TODO : There must be a better way to do that
 */
public class LoggerUtils {
    private PrintStream stream;
    LoggerUtils(PrintStream logger) {
        super();
        this.stream = logger;
    }

    /**
     * Prefix with INFO:
     * @param message String
     */
    public void info(String message) {
        stream.println("INFO:"+message);
    }
    
    /**
     * Prefix with DEBUG:
     * @param message String
     */
    public void debug(String message) {
        stream.println("DEBUG:"+message);
    }
    
    /**
     * Prefix with WARN:
     * @param messag String 
     */
    public void warn(String message) {
        stream.println("WARN:"+message);
    }
    
    /**
     * Prefix with ERROR:
     * @param message String
     */
    public void error(String message) {
        stream.println("ERROR:"+message);
    }
}
