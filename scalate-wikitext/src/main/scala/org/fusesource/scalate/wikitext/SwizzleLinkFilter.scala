/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.scalate
package wikitext

import org.fusesource.scalate.filter.Filter
import util.{Logging, IOUtil, Files}
import java.io.File
import IOUtil._

/**
 * Converts links used in wiki notation to find the template or wiki markup files on the file system.
 *
 * Wiki links tend to assume a single name is unique per wiki space; but when converting wiki pages to URIs or files
 * and directories you often want to move the wiki files into directory trees. This filter will fix up these
 * bad links, searching for wiki files in your source tree and swizzling the generated links to use those.
 */
case class SwizzleLinkFilter(sourceDirectories: Traversable[File], extensions: Set[String]) extends Filter with Logging {

  /**
   * Lets fix up any links which are local and do notcontain a file extension
   */
  def filter(context: RenderContext, html: String) = {
    debug("Transforming links with " + this)
    linkRegex.replaceAllIn(html, {
      // for some reason we don't just get the captured group - no idea why. Instead we get...
      //
      //   m.matched == m.group(0) == "<a class="foo" href='linkUri'"
      //   m.group(1) == "linkUri"
      //
      // so lets replace the link URI in the matched text to just change the contents of the link
      m =>
        val link = m.group(1)
        val matched = m.matched
        matched.dropRight(link.size + 1) + transformLink(link, context.requestUri) + matched.last
    })
  }

  /**
   * If a link is external or includes a dot then assume its OK, otherwise append html extension
   */
  def transformLink(link: String, requestUri: String) = {
    def relativeLink(link: String): String = Links.convertAbsoluteLinks(link, requestUri)

    /**
     * lets start at the root directory and keep navigating through all files until we find a file name that matches
     * the given link
     */
    def findConfluenceLink: String = {
      // for now we are just using non-path names but if we wanted to support relative badly named files
      // we could use: link.split('/').last
      val name1 = link.toLowerCase
      val name2 = name1.replace(' ', '-')


      def matchesName(f: File) = {
        val n = f.nameDropExtension.toLowerCase
        n == name1 || n == name2 && extensions.contains(f.extension.toLowerCase)
      }

      def findMatching(rootDir: File): Option[String] =
        Files.recursiveFind(rootDir)(matchesName) match {
          case Some(file) =>
            Some("/" + Files.dropExtension(Files.relativeUri(rootDir, file)))
          case _ => None
        }

      //sourceDirectories.view.map {findMatching(_)}.find(_.isDefined).getOrElse(link)
      sourceDirectories.view.map {findMatching(_)}.find(_.isDefined) match {
        case Some(Some(l)) => l
        case _ => link
      }
    }

    if (link.contains(':')) {
      // external so leave as is
      link
    } else {
      if (link.contains('.')) {
        relativeLink(link)
      }
      else {
        val newLink = if (link.contains('/')) {
          link
        } else {
          // if we have no path then assume we are a bad confluence link and try find the actual path
          findConfluenceLink
        }
        relativeLink(newLink) + ".html"
      }
    }
  }

  protected val linkRegex = "(?i)<(?>link|a|img|script)[^>]*?(?>href|src)\\s*?=\\s*?[\\\"'](.*?)[\\\"'][^>]*?".r
}