/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.exceptions

/**
 * An exception that can be thrown from the {@link org.zowe.pipelines.base.Pipeline} class
 */
class PipelineException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    PipelineException(String message) {
        super(message)
    }
}
