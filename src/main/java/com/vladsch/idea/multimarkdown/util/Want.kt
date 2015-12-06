/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>, all rights reserved.
 *
 * This code is private property of the copyright holder and cannot be used without
 * having obtained a license or prior written permission of the of the copyright holder.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.vladsch.idea.multimarkdown.util

/**
 * singleton Want : used to create a bit mask of type of link resolving results we want
 *
 * The whole mess here was created so we can easily write: Want(Local.URI, REMOTE.URL, Links.None, Match.LOOSE)
 * instead of a whole bunch of that or those or ....
 *
 * I also wanted to avoid creating objects just to setup options. I wanted all singletons and enums with the final
 * result an integer of bits for various fields without the mess of figuring out the bits and shifts and bitmask.
 *
 * The result is comfortable to use, but a bit on the heavy side as far as code quantity. The goal was comfortable,
 * flexible use and low overhead querying of options.
 *
 * Also in the resolver testing for options is likewise easy:
 *
 * val options:Int = Want(....)
 *
 * if (Want.local(options))....  : any local other than NONE
 * if (Want.remote(options)).... : any remote other than NONE
 *
 * or for specific type of result desired:
 *
 * if (Want.localType(options) == Local.URI)....
 * if (Want.remoteType(options) == Remote.URL)....
 *
 * if (Want.exactMatch(options))   : true if not loose or completion
 * if (Want.looseMatch(options))   : true if loose match was specified
 * if (Want.completionMatch(options))   : true if completion match was specified
 *
 * if (Want.links(options))        : true if Links is other than NONE
 *
 * Local is file not under Vcs, ie. not on Remote Repo site
 * Remote is file under Vcs, ie. on Remote Repo site
 * Links are just URLs we recognize and resolve for the Repo. ie. GitHub: fork, pulls, issues, pulse, graphs
 *
 * Local and Remote can be:
 *       NONE - we don't want these files, ie. Local.NONE will eliminate any files not under VCS
 *       REF - file reference, used to derive a relative address
 *       REL - a link with relative address
 *       URI - a link with file:// followed by file path, used to pass to browser for local file
 *       URL - a link with https:// to the remote repo file
 *
 *  Links are either Links.NONE or Links.URL
 *
 *  Match specifies type of matching:
 *      EXACT       - we are resolving an exact reference, we want same files as a link would
 *                  resolve on the Repo site
 *
 *      LOOSE       - we want something close so we can inspect and give user options
 *
 *      COMPLETION  - we want a list for completions (currently not implemented and an empty
 *                  file name is used as a marker that it is for completion list
 *
 * It also allows for type safety, flexible defaults based on what options were already given.
 *
 * So Want() is the same as Want(Local.REF, Remote.REF, Links.URL, Match.Exact)
 * So Want(Local.NONE) is the same as Want(Local.NONE, Remote.REF, Links.URL, Match.EXACT)
 * So Want(Remote.NONE) is the same as Want(Local.REF, Remote.NONE, Links.URL, Match.EXACT)
 * So Want(Links.NONE) is the same as Want(Local.REF, Remote.REF, Links.NONE, Match.EXACT)
 * So Want(Match.LOOSE) is the same as Want(Local.REF, Remote.REF, Links.URL, Match.LOOSE)
 *
 * Specifying local or remote option to something other than none, will eliminate the default
 * for its counterparts. Links don't get defaults if either local or remote is specified
 * So Want(Local.REF) is the same as Want(Local.REF, Remote.NONE, Links.NONE, Match.EXACT)
 * So Want(Remote.URL) is the same as Want(Local.NONE, Remote.URL, Links.NONE, Match.EXACT)
 *
 * Match.LOOSE and Match.COMPLETION can be used together, otherwise only one option of a type is allowed.
 *
 */
object Want : DataPrinterAware {
    enum class FileType(val flags: Int) {
        NONE(0),
        REF(1),
        REL(2),
        URI(3),
        URL(4);

        internal val unboxed: Int get() = FileType.unboxed(flags)

        companion object : EnumBitField<FileType>(3) {
            override fun boxed(flags: Int): FileType {
                val masked = flags(flags)
                return when (masked) {
                    REF.flags -> REF
                    URI.flags -> URI
                    URL.flags -> URL
                    REL.flags -> REL
                    else -> NONE
                }
            }
        }
    }

    enum class LocalType(val type: FileType) {
        NONE(FileType.NONE),
        REF(FileType.REF),
        REL(FileType.REL),
        URI(FileType.URI),
        URL(FileType.URL);

        internal val unboxed: Int get() = unboxed(type.unboxed)
        internal val flags: Int get() = type.flags

        companion object : EnumBitField<FileType>(FileType.bits) {
            override fun boxed(flags: Int): FileType {
                return FileType.boxed(flags(flags))
            }
        }
    }

    enum class RemoteType(val type: FileType) {
        NONE(FileType.NONE),
        REF(FileType.REF),
        REL(FileType.REL),
        URI(FileType.URI),
        URL(FileType.URL);

        internal val unboxed: Int get() = unboxed(type.unboxed)
        internal val flags: Int get() = type.flags

        companion object : EnumBitField<FileType>(FileType.bits, LocalType) {
            override fun boxed(flags: Int): FileType {
                return FileType.boxed(flags(flags))
            }
        }
    }

    enum class MatchType(val flags: Int) {
        EXACT(0),
        LOOSE(1),
        COMPLETION(2),
        LOOSE_COMPLETION(3);

        internal val unboxed: Int get() = unboxed(flags)

        companion object : EnumBitField<MatchType>(2, RemoteType) {
            override fun boxed(flags: Int): MatchType {
                val masked = flags(flags)
                return when (masked) {
                    LOOSE.flags -> LOOSE
                    COMPLETION.flags -> COMPLETION
                    LOOSE_COMPLETION.flags -> LOOSE_COMPLETION
                    else -> EXACT
                }
            }
        }
    }

    enum class LinkType(val flags: Int) {
        NONE(0),
        URL(1);

        internal val unboxed: Int get() = unboxed(flags)

        companion object : EnumBitField<LinkType>(1, MatchType) {
            override fun boxed(flags: Int): LinkType {
                val masked = flags(flags)
                return when (masked) {
                    URL.flags -> URL
                    else -> NONE
                }
            }
        }
    }

    sealed class Options : DataPrinterAware {
        abstract class Remotes : Options() {
            abstract val unboxed: RemoteType
        }

        abstract class Locals : Options() {
            abstract val unboxed: LocalType
        }

        abstract class Matches : Options() {
            abstract val unboxed: MatchType
        }

        abstract class Links : Options() {
            abstract val unboxed: LinkType
        }

        override fun testData(): String {
            return when (this) {
                is Remotes -> "Remote." + unboxed.type
                is Locals -> "Local." + unboxed.type
                is Matches -> "Match." + unboxed
                is Links -> "Links." + unboxed
            }
        }

        override fun className(inParent: java.lang.Class<Any>?): String {
            return when (this) {
                is Remotes -> "Remote"
                is Locals -> "Local"
                is Matches -> "Match"
                is Links -> "Links"
            }
        }
    }

    // @formatter:off
    object RemoteNone : Options.Remotes() { override val unboxed:RemoteType get() = RemoteType.NONE }
    object RemoteRef : Options.Remotes() { override val unboxed:RemoteType get() = RemoteType.REF }
    object RemoteRel : Options.Remotes() { override val unboxed:RemoteType get() = RemoteType.REL }
    object RemoteUri : Options.Remotes() { override val unboxed:RemoteType get() = RemoteType.URI }
    object RemoteUrl : Options.Remotes() { override val unboxed:RemoteType get() = RemoteType.URL }

    object LocalNone : Options.Locals() { override val unboxed:LocalType get() = LocalType.NONE }
    object LocalRef : Options.Locals() { override val unboxed:LocalType get() = LocalType.REF }
    object LocalRel : Options.Locals() { override val unboxed:LocalType get() = LocalType.REL }
    object LocalUri : Options.Locals() { override val unboxed:LocalType get() = LocalType.URI }
    object LocalUrl : Options.Locals() { override val unboxed:LocalType get() = LocalType.URL }

    object MatchExact : Options.Matches() { override val unboxed:MatchType get() = MatchType.EXACT }
    object MatchLoose : Options.Matches() { override val unboxed:MatchType get() = MatchType.LOOSE }
    object MatchCompletion : Options.Matches() { override val unboxed:MatchType get() = MatchType.COMPLETION }
    object MatchLooseCompletion : Options.Matches() { override val unboxed:MatchType get() = MatchType.LOOSE_COMPLETION }

    object LinksNone : Options.Links() { override val unboxed:LinkType get() = LinkType.NONE }
    object LinksUrl : Options.Links() { override val unboxed:LinkType get() = LinkType.URL }
    // @formatter:on

    fun assertNull(option: Options?) {
        assert(option == null, { "option ${(option as Options).className()} is already set to ${option.testData()}" })
    }

    fun unboxed(vararg options: Options?): Int {
        var remote: Options.Remotes? = null
        var local: Options.Locals? = null
        var match: Options.Matches? = null
        var links: Options.Links? = null
        for (option in options) {
            if (option != null) {
                when (option) {
                    is Options.Remotes -> {
                        assertNull(remote)
                        remote = option
                    }
                    is Options.Locals -> {
                        assertNull(local)
                        local = option
                    }
                    is Options.Matches -> {
                        assertNull(match)
                        match = option
                    }
                    is Options.Links -> {
                        assertNull(links)
                        links = option
                    }
                }
            }
        }

        var flags: Int = 0

        if (local != null || remote == null || remote == RemoteNone) flags = flags or (local?.unboxed ?: LocalRef.unboxed).unboxed
        if (remote != null || local == null || local == LocalNone) flags = flags or (remote?.unboxed ?: RemoteRef.unboxed).unboxed
        flags = flags or (match?.unboxed ?: MatchExact.unboxed).unboxed

        // this one only gets a default if nothing but match is provided
        if (links != null || ((local == null || local == LocalNone) && (remote == null || remote == RemoteNone))) flags = flags or (links?.unboxed ?: LinksUrl.unboxed).unboxed

        return flags
    }

    fun testData(options: Int): String {
        // print the options as our Want(...) processing params explicitly so tests won't change if defaults change
        var optionList = arrayListOf<String>()
        optionList.add(localType(options).testData())
        optionList.add(remoteType(options).testData())
        if (links(options)) optionList.add(linksType(options).testData())
        optionList.add(matchType(options).testData())

        val result = "Want(" + optionList.splice(", ") + ")"
        return result
    }

    operator fun invoke(vararg options: Options?) = unboxed(*options)

    fun localType(options: Int): Options.Locals = Local.boxed(LocalType.boxed(options))
    fun remoteType(options: Int): Options.Remotes = Remote.boxed(RemoteType.boxed(options))
    fun matchType(options: Int): Options.Matches = Match.boxed(MatchType.boxed(options))
    fun linksType(options: Int): Options.Links = Links.boxed(LinkType.boxed(options))
    fun exactMatch(options: Int): Boolean = MatchType.unboxedFlags(options) == MatchType.EXACT.unboxed
    fun looseMatch(options: Int): Boolean = MatchType.unboxedFlags(options) == MatchType.LOOSE.unboxed
    fun completionMatch(options: Int): Boolean = MatchType.unboxedFlags(options) == MatchType.COMPLETION.unboxed
    fun looseCompletionMatch(options: Int): Boolean = MatchType.unboxedFlags(options) == MatchType.LOOSE_COMPLETION.unboxed
    fun links(options: Int): Boolean = LinkType.unboxedFlags(options) == LinkType.URL.unboxed
    fun local(options: Int): Boolean = LocalType.unboxedFlags(options) != LocalType.NONE.unboxed
    fun localREF(options: Int): Boolean = LocalType.unboxedFlags(options) == LocalType.REF.unboxed
    fun localREL(options: Int): Boolean = LocalType.unboxedFlags(options) == LocalType.REL.unboxed
    fun localURI(options: Int): Boolean = LocalType.unboxedFlags(options) == LocalType.URI.unboxed
    fun localURL(options: Int): Boolean = LocalType.unboxedFlags(options) == LocalType.URL.unboxed
    fun remote(options: Int): Boolean = RemoteType.unboxedFlags(options) != RemoteType.NONE.unboxed
    fun remoteREF(options: Int): Boolean = RemoteType.unboxedFlags(options) == RemoteType.REF.unboxed
    fun remoteREL(options: Int): Boolean = RemoteType.unboxedFlags(options) == RemoteType.REL.unboxed
    fun remoteURI(options: Int): Boolean = RemoteType.unboxedFlags(options) == RemoteType.URI.unboxed
    fun remoteURL(options: Int): Boolean = RemoteType.unboxedFlags(options) == RemoteType.URL.unboxed
}

object Local {
    @JvmStatic val NONE: Want.Options.Locals get() = Want.LocalNone
    @JvmStatic val REF: Want.Options.Locals get() = Want.LocalRef
    @JvmStatic val REL: Want.Options.Locals get() = Want.LocalRel
    @JvmStatic val URI: Want.Options.Locals get() = Want.LocalUri
    @JvmStatic val URL: Want.Options.Locals get() = Want.LocalUrl

    fun boxed(fileType: Want.FileType): Want.Options.Locals {
        return when (fileType) {
            Want.FileType.REL -> REL
            Want.FileType.REF -> REF
            Want.FileType.URI -> URI
            Want.FileType.URL -> URL
            else -> NONE
        }
    }
}

object Remote {
    @JvmStatic val NONE: Want.Options.Remotes get() = Want.RemoteNone
    @JvmStatic val REF: Want.Options.Remotes get() = Want.RemoteRef
    @JvmStatic val REL: Want.Options.Remotes get() = Want.RemoteRel
    @JvmStatic val URI: Want.Options.Remotes get() = Want.RemoteUri
    @JvmStatic val URL: Want.Options.Remotes get() = Want.RemoteUrl

    fun boxed(fileType: Want.FileType): Want.Options.Remotes {
        return when (fileType) {
            Want.FileType.REF -> REF
            Want.FileType.REL -> REL
            Want.FileType.URI -> URI
            Want.FileType.URL -> URL
            else -> NONE
        }
    }
}

object Match {
    @JvmStatic val EXACT: Want.Options.Matches get() = Want.MatchExact
    @JvmStatic val LOOSE: Want.Options.Matches get() = Want.MatchLoose
    @JvmStatic val COMPLETION: Want.Options.Matches get() = Want.MatchCompletion
    @JvmStatic val LOOSE_COMPLETION: Want.Options.Matches get() = Want.MatchLooseCompletion

    fun boxed(fileType: Want.MatchType): Want.Options.Matches {
        return when (fileType) {
            Want.MatchType.LOOSE -> LOOSE
            Want.MatchType.COMPLETION -> COMPLETION
            Want.MatchType.LOOSE_COMPLETION -> LOOSE_COMPLETION
            else -> EXACT
        }
    }
}

object Links {
    @JvmStatic val NONE: Want.Options.Links get() = Want.LinksNone
    @JvmStatic val URL: Want.Options.Links get() = Want.LinksUrl

    fun boxed(fileType: Want.LinkType): Want.Options.Links {
        return when (fileType) {
            Want.LinkType.URL -> URL
            else -> NONE
        }
    }
}
