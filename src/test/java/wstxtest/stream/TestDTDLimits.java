package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.exc.WstxLazyException;
import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Tests that verify that nesting of content specification groups in
 * DTD element declarations is bounded (see
 * {@link WstxInputProperties#P_MAX_DTD_DEPTH}), so that maliciously
 * deep nesting is reported as a limit violation instead of exhausting
 * the call stack.
 */
public class TestDTDLimits
    extends BaseStreamTest
{
    // Far deeper than any thread stack can handle via unbounded recursion
    private final static int MALICIOUS_DEPTH = 50000;

    public void testDefaultMaxDtdDepth() throws XMLStreamException
    {
        XMLInputFactory f = getNewInputFactory();
        assertEquals(Integer.valueOf(ReaderConfig.DEFAULT_MAX_DTD_DEPTH),
                f.getProperty(WstxInputProperties.P_MAX_DTD_DEPTH));
        assertEquals(500, ReaderConfig.DEFAULT_MAX_DTD_DEPTH);

        f.setProperty(WstxInputProperties.P_MAX_DTD_DEPTH, Integer.valueOf(42));
        assertEquals(Integer.valueOf(42),
                f.getProperty(WstxInputProperties.P_MAX_DTD_DEPTH));
    }

    public void testDeepNestingInInternalSubset() throws XMLStreamException
    {
        XMLInputFactory f = getNewInputFactory();
        String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+nestedSpec(MALICIOUS_DEPTH)+">\n"
                +"]><root />";
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        verifyDtdDepthFailure(sr, ReaderConfig.DEFAULT_MAX_DTD_DEPTH);
    }

    public void testDeepNestingInInternalSubsetValidating() throws XMLStreamException
    {
        // with validation enabled, content specs are also constructed
        XMLInputFactory f = getNewInputFactory();
        setValidating(f, true);
        String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+nestedSpec(MALICIOUS_DEPTH)+">\n"
                +"<!ELEMENT a EMPTY>\n"
                +"]><root><a /></root>";
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        verifyDtdDepthFailure(sr, ReaderConfig.DEFAULT_MAX_DTD_DEPTH);
    }

    public void testDeepNestingInExternalSubset() throws XMLStreamException
    {
        XMLInputFactory f = getNewInputFactory();
        setSupportExternalEntities(f, true);
        f.setXMLResolver(new SimpleResolver("<!ELEMENT root "
                +nestedSpec(MALICIOUS_DEPTH)+">\n"));
        String XML = "<!DOCTYPE root SYSTEM 'ext.dtd'><root />";
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        verifyDtdDepthFailure(sr, ReaderConfig.DEFAULT_MAX_DTD_DEPTH);
    }

    public void testCustomMaxDtdDepth() throws XMLStreamException
    {
        // 11 parenthesis groups -> innermost group at nesting depth of 10
        final String XML_OK = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+nestedSpec(11)+">\n"
                +"]><root />";
        final String XML_FAIL = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+nestedSpec(12)+">\n"
                +"]><root />";

        // fine with default limits
        XMLInputFactory f = getNewInputFactory();
        streamThroughAndClose(f, XML_OK);
        streamThroughAndClose(f, XML_FAIL);

        // and at the limit
        f.setProperty(WstxInputProperties.P_MAX_DTD_DEPTH, Integer.valueOf(10));
        streamThroughAndClose(f, XML_OK);

        // but not above it
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML_FAIL));
        verifyDtdDepthFailure(sr, 10);

        // and config must also carry over to readers via Stax2 factory configs
        WstxInputFactory wf = new WstxInputFactory();
        wf.getConfig().setMaxDtdDepth(10);
        assertEquals(10, wf.getConfig().getMaxDtdDepth());
        sr = wf.createXMLStreamReader(new StringReader(XML_FAIL));
        verifyDtdDepthFailure(sr, 10);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    private static String nestedSpec(int depth)
    {
        StringBuilder sb = new StringBuilder(depth + depth + 1);
        for (int i = 0; i < depth; ++i) {
            sb.append('(');
        }
        sb.append('a');
        for (int i = 0; i < depth; ++i) {
            sb.append(')');
        }
        return sb.toString();
    }

    private void streamThroughAndClose(XMLInputFactory f, String xml)
        throws XMLStreamException
    {
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(xml));
        streamThrough(sr);
        sr.close();
    }

    private void verifyDtdDepthFailure(XMLStreamReader sr, int limit)
        throws XMLStreamException
    {
        try {
            streamThrough(sr);
            fail("Should have failed with DTD nesting depth limit ("+limit+") exceeded");
        } catch (WstxLazyException e) {
            verifyException(e, "Maximum DTD nesting depth");
            verifyException(e, "("+limit+")");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum DTD nesting depth");
            verifyException(e, "("+limit+")");
        }
        sr.close();
    }
}
