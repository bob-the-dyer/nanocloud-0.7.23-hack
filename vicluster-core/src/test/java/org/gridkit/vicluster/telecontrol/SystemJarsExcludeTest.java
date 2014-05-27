package org.gridkit.vicluster.telecontrol;

import org.junit.Test;

/**
 * User: Vladimir Krasilschik (VlKrasil)
 * Date: 26/05/14
 * Time: 22:56
 */
public class SystemJarsExcludeTest {

    @Test
    public void test_getJreRoot(){
        Classpath.getJreRoot();
        Classpath.getJdkLib();

    }
}
