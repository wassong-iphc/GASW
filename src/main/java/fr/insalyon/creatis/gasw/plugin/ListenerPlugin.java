/* Copyright CNRS-CREATIS
 *
 * Rafael Ferreira da Silva
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
package fr.insalyon.creatis.gasw.plugin;

import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswOutput;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.bean.JobMinorStatus;
import java.util.List;
import net.xeoh.plugins.base.Plugin;

/**
 *
 * @author Rafael Silva
 */
public interface ListenerPlugin extends Plugin {

    public String getPluginName();

    /**
     * Gets a list of persistent classes to be loaded in Hibernate.
     *
     * @return List of persistent classes
     * @throws GaswException
     */
    public List<Class> getPersistentClasses() throws GaswException;
    
    public void load() throws GaswException;

    public void jobSubmitted(Job job) throws GaswException;

    public void jobFinished(GaswOutput gaswOutput) throws GaswException;

    public void jobStatusChanged(Job job) throws GaswException;

    public void jobMinorStatusReported(JobMinorStatus jobMinorStatus) throws GaswException;

    public void terminate() throws GaswException;
}
