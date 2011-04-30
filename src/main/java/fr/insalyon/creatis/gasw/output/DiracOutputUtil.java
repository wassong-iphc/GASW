/* Copyright CNRS-CREATIS
 *
 * Rafael Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.creatis.insa-lyon.fr/~silva
 *
 * This software is a grid-enabled data-driven workflow manager and editor.
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw.output;

import fr.insalyon.creatis.gasw.Constants;
import fr.insalyon.creatis.gasw.GaswOutput;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.dao.DAOFactory;
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.monitor.Monitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Silva
 */
public class DiracOutputUtil extends OutputUtil {

    private static final Logger logger = Logger.getLogger(DiracOutputUtil.class);

    public DiracOutputUtil(int startTime) {
        super(startTime);
    }

    public GaswOutput getOutputs(String jobID) {
        return getOutputs(jobID, "");
    }

    public GaswOutput getOutputs(String jobID, String proxyFile) {
        try {
            JobDAO jobDAO = DAOFactory.getDAOFactory().getJobDAO();
            Job job = jobDAO.getJobByID(jobID);

            if (job.getStatus() != Monitor.Status.CANCELLED && job.getStatus() != Monitor.Status.STALLED) {

                ProcessBuilder builder = new ProcessBuilder(
                        "dirac-wms-job-get-output", jobID);

                builder.redirectErrorStream(true);

                if (!proxyFile.isEmpty() && proxyFile != null) {
                    builder.environment().put("X509_USER_PROXY", proxyFile);
                }

                Process process = builder.start();
                process.waitFor();

                if (process.exitValue() == 0) {
                    File stdOut = getStdFile(job, ".out", Constants.OUT_ROOT);
                    File stdErr = getStdFile(job, ".err", Constants.ERR_ROOT);

                    File outTempDir = new File("./" + jobID);
                    outTempDir.delete();

                    int exitCode = parseStdOut(job, stdOut);
                    exitCode = parseStdErr(job, stdErr, exitCode);

                    File appStdOut = saveFile(job, ".app.out", Constants.OUT_ROOT, getAppStdOut());
                    File appStdErr = saveFile(job, ".app.err", Constants.ERR_ROOT, getAppStdErr());

                    return new GaswOutput(jobID, exitCode, appStdOut, appStdErr, stdOut, stdErr);
                    
                } else {
                    
                    BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String cout = "";
                    String s = null;
                    while ((s = r.readLine()) != null) {
                        cout += s;
                    }
                    
                    logger.error(cout);
                    logger.error("Output files does not exist. Job ID: " + jobID);

                    File stdOut = saveFile(job, ".out", Constants.OUT_ROOT, "Output files does not exist.");
                    File stdErr = saveFile(job, ".err", Constants.ERR_ROOT, "Output files does not exist.");
                    File appStdOut = saveFile(job, ".app.out", Constants.OUT_ROOT, "");
                    File appStdErr = saveFile(job, ".app.err", Constants.ERR_ROOT, "");

                    return new GaswOutput(jobID, 6, appStdOut, appStdErr, stdOut, stdErr);
                }

            } else {

                String message = "";
                int exitCode = 0;

                if (job.getStatus() == Monitor.Status.CANCELLED) {
                    message = "Job Cancelled";
                } else {
                    message = "Job Stalled";
                    exitCode = 6;
                }

                File stdOut = saveFile(job, ".out", Constants.OUT_ROOT, message);
                File stdErr = saveFile(job, ".err", Constants.ERR_ROOT, message);

                return new GaswOutput(jobID, exitCode, stdOut, stdErr, stdOut, stdErr);
            }

        } catch (DAOException ex) {
            logException(logger, ex);
        } catch (InterruptedException ex) {
            logException(logger, ex);
        } catch (IOException ex) {
            logException(logger, ex);
        }
        return null;
    }

    /**
     * 
     * 
     * @param job Job object
     * @param extension File extension
     * @param directory Output directory
     * @return
     */
    private File getStdFile(Job job, String extension, String directory) {
        File stdDir = new File(directory);
        if (!stdDir.exists()) {
            stdDir.mkdir();
        }
        File stdFile = new File("./" + job.getId() + "/std" + extension);
        File stdRenamed = new File(directory + "/" + job.getFileName() + ".sh" + extension);
        stdFile.renameTo(stdRenamed);
        return stdRenamed;
    }
}
