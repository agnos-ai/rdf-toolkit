/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Enterprise Data Management Council
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.edmcouncil.rdf_toolkit.owlapi_serializer

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Document

class RdfXmlSorter private (input: Path) {

  def xmlDocument: Document = {

    val file = input.toFile
    val dbFactory = DocumentBuilderFactory.newInstance
    val dBuilder = dbFactory.newDocumentBuilder()
    val doc = dBuilder.parse(file)

    //
    // optional, but recommended
    //
    // read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
    //
    doc.getDocumentElement.normalize()
    doc
  }

  //  def sortedAsString = org.ow2.easywsdl.tooling.java2wsdl.util.XMLSorter.sort(xmlDocument) // TODO: [ABC] had to comment this out, not downloaded by build.sbt

  //  def printIt() = { // TODO: [ABC] had to comment this out, not downloaded by build.sbt
  //    print(sortedAsString)
  //  }

}

object RdfXmlSorter {

  def apply(path: Path) = new RdfXmlSorter(path)
}
