/* Copyright CNRS-CREATIS
 *
 * Rafael Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.rafaelsilva.com
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
package fr.insalyon.creatis.gasw.execution;

import fr.insalyon.creatis.gasw.GaswConstants;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswNotification;
import fr.insalyon.creatis.gasw.GaswOutput;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.bean.JobMinorStatus;
import fr.insalyon.creatis.gasw.bean.Node;
import fr.insalyon.creatis.gasw.bean.NodeID;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.dao.DAOFactory;
import grool.proxy.Proxy;
import java.io.*;
import java.net.URI;
import java.util.*;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Silva
 */
public abstract class GaswOutputParser extends Thread {

    private static final Logger logger = Logger.getLogger("fr.insalyon.creatis.gasw");
    protected Job job;
    protected Proxy userProxy;
    protected File appStdOut;
    protected File appStdErr;
    private BufferedWriter appStdOutWriter;
    private BufferedWriter appStdErrWriter;
    protected List<URI> uploadedResults = null;
    private StringBuilder inputsDownloadErrBuf;
    private StringBuilder resultsUploadErrBuf;
    private StringBuilder appStdOutBuf;
    private StringBuilder appStdErrBuf;

    /**
     *
     * @param jobID job identification
     * @param proxy associated proxy (null in case using default proxy)
     */
    public GaswOutputParser(String jobID, Proxy userProxy) {

        try {
            this.job = DAOFactory.getDAOFactory().getJobDAO().getJobByID(jobID);
            this.userProxy = userProxy;

            this.appStdOut = getAppStdFile(GaswConstants.OUT_APP_EXT, GaswConstants.OUT_ROOT);
            this.appStdErr = getAppStdFile(GaswConstants.ERR_APP_EXT, GaswConstants.ERR_ROOT);

            this.appStdOutWriter = new BufferedWriter(new FileWriter(appStdOut));
            this.appStdErrWriter = new BufferedWriter(new FileWriter(appStdErr));

            this.inputsDownloadErrBuf = new StringBuilder();
            this.resultsUploadErrBuf = new StringBuilder();
            this.appStdOutBuf = new StringBuilder();
            this.appStdErrBuf = new StringBuilder();

        } catch (IOException ex) {
            logger.error(ex);
        } catch (DAOException ex) {
            logger.error(ex);
        }
    }

    @Override
    public void run() {

        try {
            GaswNotification.getInstance().addFinishedJob(getGaswOutput());
        } catch (GaswException ex) {
            // do nothing
        }
    }

    /**
     * Gets the standard output and error files and exit code.
     *
     * @return Gasw output object with the standard output and error files
     * respectively.
     * @throws GaswException
     */
    public abstract GaswOutput getGaswOutput() throws GaswException;

    /**
     *
     * @param stdOut Standard output file
     * @return Exit code
     */
    protected int parseStdOut(File stdOut) {


        int exitCode = -1;

        try {
            if (job.getQueued() == null) {
                job.setQueued(job.getCreation());
            }

            Node node = new Node();
            NodeID nodeID = new NodeID();
            Scanner scanner = new Scanner(new FileInputStream(stdOut));

            boolean isAppExec = false;
            boolean isResultUpload = false;
            String lfcHost = "";

            try {
                while (scanner.hasNextLine()) {

                    String line = scanner.nextLine();

                    // Application Output
                    if (line.contains("<application_execution>")) {
                        isAppExec = true;
                    } else if (line.contains("</application_execution>")) {
                        isAppExec = false;
                    } else if (isAppExec) {
                        appStdOutWriter.write(line + "\n");
                        appStdOutBuf.append(line).append("\n");
                    }

                    // General Output
                    if (line.contains("Input download time:")) {
                        int downloadTime = Integer.valueOf(line.split(" ")[13]).intValue();
                        job.setRunning(addDate(job.getDownload(), Calendar.SECOND, downloadTime));

                    } else if (line.contains("Execution time:")) {
                        int executionTime = Integer.valueOf(line.split(" ")[12]).intValue();
                        job.setUpload(addDate(job.getRunning(), Calendar.SECOND, executionTime));

                    } else if (line.contains("Results upload time:")) {
                        int uploadTime = Integer.valueOf(line.split(" ")[13]).intValue();
                        job.setEnd(addDate(job.getUpload(), Calendar.SECOND, uploadTime));

                    } else if (line.contains("Exiting with return value")) {
                        String[] errmsg = line.split("\\s+");
                        exitCode = Integer.valueOf(errmsg[errmsg.length - 1]).intValue();
                        job.setExitCode(exitCode);

                    } else if (line.startsWith("SITE_NAME")) {
                        nodeID.setSiteName(line.split("=")[1]);

                    } else if (line.startsWith("PBS_O_HOST") && nodeID.getSiteName() == null) {
                        nodeID.setSiteName(line.split("=")[1]);
                        String code = nodeID.getNodeName().substring(nodeID.getNodeName().lastIndexOf(".") + 1, nodeID.getNodeName().length());
                        if (code.length() != 2) {
                            String host = line.split("=")[1];
                            String countryCode = host.substring(host.lastIndexOf("."), host.length());
                            nodeID.setNodeName(nodeID.getNodeName() + countryCode);
                        }
                    } else if (line.startsWith("===== uname =====")) {
                        line = scanner.nextLine();
                        nodeID.setNodeName(line.split(" ")[1]);

                    } else if (line.startsWith("CE_ID")) {
                        String code = nodeID.getNodeName().substring(nodeID.getNodeName().lastIndexOf(".") + 1, nodeID.getNodeName().length());
                        if (code.length() != 2) {
                            String host = URI.create("http://" + line.split("=")[1]).getHost();
                            String countryCode = host.substring(host.lastIndexOf("."), host.length());
                            nodeID.setNodeName(nodeID.getNodeName() + countryCode);
                        }

                    } else if (line.startsWith("processor")) {
                        node.setnCpus(new Integer(line.split(":")[1].trim()) + 1);

                    } else if (line.startsWith("model name")) {
                        node.setCpuModelName(line.split(":")[1].trim());

                    } else if (line.startsWith("cpu MHz")) {
                        node.setCpuMhz(new Double(line.split(":")[1].trim()));

                    } else if (line.startsWith("cache size")) {
                        node.setCpuCacheSize(new Integer(line.split(":")[1].trim().split(" ")[0]));

                    } else if (line.startsWith("bogomips")) {
                        node.setCpuBogoMips(new Double(line.split(":")[1].trim()));

                    } else if (line.startsWith("MemTotal:")) {
                        node.setMemTotal(new Integer(line.split("\\s+")[1]));

                    } else if (line.startsWith("<results_upload>")) {
                        isResultUpload = true;
                        uploadedResults = new ArrayList<URI>();

                    } else if (line.startsWith("</results_upload>")) {
                        isResultUpload = false;

                    } else if (line.startsWith("LFC_HOST")) {
                        lfcHost = line.substring(line.indexOf("=") + 1);

                    } else if (line.startsWith("<file_upload") && isResultUpload) {
                        String uploadedFile = line.substring(
                                line.indexOf("=") + 1, line.length() - 1);
                        URI uri = lfcHost.isEmpty()
                                ? URI.create("file://" + uploadedFile)
                                : URI.create("lfn://" + lfcHost + uploadedFile);
                        uploadedResults.add(uri);
                    }
                }
            } finally {
                scanner.close();
            }
            appStdOutWriter.close();

            DAOFactory factory = DAOFactory.getDAOFactory();
            if (nodeID.getSiteName() != null && nodeID.getNodeName() != null) {
                node.setNodeID(nodeID);
                factory.getNodeDAO().add(node);
                job.setNode(node);
            }

            // Parse checkpoint
            job = parseCheckpoint();

            // Update Job            
            factory.getJobDAO().update(job);

        } catch (DAOException ex) {
            // do nothing
        } catch (IOException ex) {
            logger.error(ex);
        }
        return exitCode;
    }

    /**
     *
     * @param exitCode
     * @return Exit code
     */
    protected int parseStdErr(File stdErr, int exitCode) {


        try {
            Scanner scanner = new Scanner(new FileInputStream(stdErr));

            try {
                boolean isAppExec = false;
                boolean isInputsDownload = false;
                boolean isResultsUpload = false;
                boolean isUploadTest = false;

                while (scanner.hasNext()) {

                    String line = scanner.nextLine();

                    // Application Error
                    if (line.contains("<application_execution>")) {
                        isAppExec = true;

                    } else if (line.contains("</application_execution>")) {
                        isAppExec = false;

                    } else if (line.contains("<inputs_download>")) {
                        isInputsDownload = true;

                    } else if (line.contains("</inputs_download>")) {
                        isInputsDownload = false;

                    } else if (line.contains("<results_upload>")) {
                        isResultsUpload = true;

                    } else if (line.contains("</results_upload>")) {
                        isResultsUpload = false;

                    } else if (line.contains("<upload_test>")) {
                        isUploadTest = true;

                    } else if (line.contains("</upload_test>")) {
                        isUploadTest = false;

                    } else if (isAppExec) {
                        appStdErrWriter.write(line + "\n");
                        appStdErrBuf.append(line).append("\n");

                    } else if (isInputsDownload) {
                        inputsDownloadErrBuf.append(line).append("\n");

                    } else if (isResultsUpload) {
                        resultsUploadErrBuf.append(line).append("\n");

                    } else if (isUploadTest) {
                        resultsUploadErrBuf.append(line).append("\n");
                    }

                    if (line.contains("Exiting with return value")) {
                        String[] errmsg = line.split("\\s+");
                        exitCode = Integer.valueOf(errmsg[errmsg.length - 1]).intValue();
                        job.setExitCode(exitCode);
                    }
                }
            } finally {
                scanner.close();
            }
            appStdErrWriter.close();
            DAOFactory.getDAOFactory().getJobDAO().update(job);

        } catch (DAOException ex) {
            logger.error(ex);

        } catch (IOException ex) {
            logger.error(ex);
        }
        return exitCode;
    }

    /**
     *
     * @param job
     * @param exitCode
     */
    protected void parseNonStdOut(int exitCode) {

        try {
            DAOFactory factory = DAOFactory.getDAOFactory();

            for (JobMinorStatus minorStatus : factory.getJobMinorStatusDAO().getExecutionMinorStatus(job.getId())) {

                switch (minorStatus.getStatus()) {

                    case Application:
                        job.setRunning(minorStatus.getDate());
                        break;
                    case Outputs:
                        job.setUpload(minorStatus.getDate());
                }
            }
            job = parseCheckpoint();
            job.setExitCode(exitCode);
            factory.getJobDAO().update(job);

        } catch (DAOException ex) {
            logger.error(ex);
        }
    }

    /**
     *
     * @param job
     * @return
     */
    private Job parseCheckpoint() {

        try {
            DAOFactory factory = DAOFactory.getDAOFactory();

            List<JobMinorStatus> list = factory.getJobMinorStatusDAO().getCheckpoints(job.getId());

            if (!list.isEmpty()) {
                int sumCheckpointInit = 0;
                int sumCheckpointUpload = 0;
                long startCheckpoint = -1;
                long startUpload = -1;

                for (JobMinorStatus minorStatus : list) {

                    if (minorStatus.getStatus() == GaswMinorStatus.CheckPoint_Init) {
                        startCheckpoint = minorStatus.getDate().getTime();

                    } else if (minorStatus.getStatus() == GaswMinorStatus.CheckPoint_Upload
                            && startCheckpoint != -1) {

                        startUpload = minorStatus.getDate().getTime();
                        sumCheckpointInit += (int) (startUpload - startCheckpoint) / 1000;
                        startCheckpoint = -1;

                    } else if (minorStatus.getStatus() == GaswMinorStatus.CheckPoint_End
                            && startUpload != -1) {

                        sumCheckpointUpload += (int) (minorStatus.getDate().getTime() - startUpload) / 1000;
                        startUpload = -1;
                    }
                }

                job.setCheckpointInit(sumCheckpointInit);
                job.setCheckpointUpload(sumCheckpointUpload);
            }
        } catch (DAOException ex) {
            logger.error(ex);
        }
        return job;
    }

    /**
     *
     * @param job Job object
     * @param extension File extension
     * @param dir Output directory
     * @param content File content
     * @return
     */
    protected File saveFile(String extension, String dir, String content) {
        FileWriter fstream = null;
        try {
            File stdDir = new File(dir);
            if (!stdDir.exists()) {
                stdDir.mkdir();
            }
            File stdFile = new File(dir + "/" + job.getFileName() + ".sh" + extension);
            fstream = new FileWriter(stdFile);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(content);
            out.close();

            return stdFile;

        } catch (IOException ex) {
            logger.error(ex);
        } finally {
            try {
                fstream.close();
            } catch (IOException ex) {
                logger.error(ex);
            }
        }
        return null;
    }

    /**
     *
     * @param extension
     * @param dir
     * @return
     */
    private File getAppStdFile(String extension, String dir) {

        File stdDir = new File(dir);
        if (!stdDir.exists()) {
            stdDir.mkdirs();
        }
        return new File(dir + "/" + job.getFileName() + ".sh" + extension);
    }

    protected String getInputsDownloadErr() {
        return inputsDownloadErrBuf.toString();
    }

    protected String getResultsUploadErr() {
        return resultsUploadErrBuf.toString();
    }

    protected String getAppStdErr() {
        return appStdErrBuf.toString();
    }

    protected String getAppStdOut() {
        return appStdOutBuf.toString();
    }

    /**
     *
     * @param dateToBeAdded
     * @param field
     * @param amount
     * @return
     */
    private Date addDate(Date dateToBeAdded, int field, int amount) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateToBeAdded);
        calendar.add(field, amount);
        return calendar.getTime();
    }
}