package net.cumba.cdisc.define;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.NonNull;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * Some support functions when working with the ODM structure of a define XML.
 */
public class DefineSupport
{

    private static Predicate<INamedElement> getNamePredicate(@Nullable String aName,
            boolean aCompareCaseInsensitive)
    {
        if (aCompareCaseInsensitive)
        {
            return e -> equalsIgnoreCaseNullSafe(e.getName(), aName);
        }
        else
        {
            return e -> Objects.equals(e.getName(), aName);
        }
    }


    /**
     * Null-safe case-insensitive equality. {@link CDT#equalsIgnoreCase} declares both parameters
     * {@code @NonNull}, so the null handling (two {@code null}s are equal, one {@code null} is not)
     * is performed here before delegating; this preserves the original null-tolerant contract.
     */
    private static boolean equalsIgnoreCaseNullSafe(@Nullable String aLeft, @Nullable String aRight)
    {
        if (aLeft == null || aRight == null)
        {
            // both null => equal; exactly one null => not equal
            return aLeft == null && aRight == null;
        }
        return CDT.equalsIgnoreCase(aLeft, aRight);
    }


    public static <T extends INamedElement> Optional<T> filterByName(@Nullable Stream<T> aStream,
            @Nullable String aName, boolean aCompareCaseInsensitive)
    {
        if (aStream == null)
        {
            return Optional.empty();
        }

        return aStream//
                .filter(Objects::nonNull)//
                .filter(getNamePredicate(aName, aCompareCaseInsensitive))//
                .findAny();
    }


    public static @Nullable String getDescriptionString(@Nullable IDescribedElement aElement)
    {
        if (aElement == null)
        {
            return null;
        }
        return getDescriptionString(aElement.getDescription());
    }


    public static @Nullable String getDescriptionString(@Nullable Description aDescription)
    {
        if (aDescription == null)
        {
            return null;
        }
        List<TranslatedText> translations = aDescription.getTranslatedTexts();
        if (translations == null || translations.isEmpty())
        {
            return null;
        }
        return translations.get(0).getValue();
    }

    /**
     *
     */
    @Getter
    @NonNull
    private final URI uri;

    @Getter
    @NonNull
    private final ODM odm;

    private boolean namesCaseInsensitive = true;

    /**
     * Create a new instance from the given file.
     *
     * @param aFile
     *            the file to load the {@link ODM} element from and use the URI from.
     * @throws IOException
     *             in case loading failed.
     */
    public DefineSupport(File aFile) throws IOException
    {
        this(aFile.toURI());
    }


    /**
     * Create a new instance from the given URI. This method uses the {@link DefineXmlParser} to
     * parse the content of the element referenced by the given URI.
     *
     * @param aURI
     *            the uri to load the {@link ODM} element from.
     * @throws IOException
     *             in case loading failed.
     */
    public DefineSupport(URI aURI) throws IOException
    {
        this(aURI, new DefineXmlParser().parse(aURI));
    }


    /**
     * Create a new instance from the give ODM and file.
     *
     * @param aFile
     *            the file to derive the URI from.
     * @param aOdm
     *            the ODM element to use as define xml.
     */
    public DefineSupport(File aFile, ODM aOdm)
    {
        this(aFile.toURI(), aOdm);
    }


    /**
     * Create a new instance from the give ODM and URI.
     *
     * @param aURI
     *            the URI that is the reference to the define.
     * @param aOdm
     *            the ODM element to use as define xml.
     */
    public DefineSupport(@NonNull URI aURI, @NonNull ODM aOdm)
    {
        uri = aURI;
        odm = aOdm;
    }


    /**
     * Internal null save helper method that converts a List into a Stream.
     *
     * @param <T>
     *            the type of the list and stream elements.
     * @param aList
     *            the list
     * @return the stream. This might be an empty stream if given List is null.
     */
    private <T> Stream<T> list2Stream(@Nullable List<T> aList)
    {
        return aList == null ? Stream.empty() : aList.stream();
    }


    /**
     * Internal null save helper method to retrieve a stream of {@link Standard}s from the given
     * element.
     *
     * @param aMdv
     *            the metadata element to retrieve the stream of standards from.
     * @return the stream of standards, that might be empty, but never null.
     */
    private Stream<Standard> map2Standard(@Nullable MetaDataVersion aMdv)
    {
        if (aMdv == null)
        {
            return Stream.empty();
        }
        Standards stds = aMdv.getStandards();
        if (stds == null)
        {
            return Stream.empty();
        }
        return list2Stream(stds.getStandards());
    }


    public Stream<Study> getStudies()
    {
        List<Study> studies = odm.getStudies();
        if (studies == null)
        {
            return Stream.empty();
        }
        return studies.stream()//
                .filter(Objects::nonNull);
    }


    public Stream<GlobalVariables> getGlobalVariables()
    {
        return getStudies()//
                .map(Study::getGlobalVariables)//
                .filter(Objects::nonNull);
    }


    public Stream<MetaDataVersion> getMetaDataVersions()
    {
        return getStudies()//
                .flatMap(s -> list2Stream(s.getMetaDataVersions()))//
                .filter(Objects::nonNull);
    }


    public Stream<ItemGroupDef> getItemGroupDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getItemGroupDefs()))//
                .filter(Objects::nonNull);
    }


    public Stream<ItemDef> getItemDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getItemDefs()))//
                .filter(Objects::nonNull);
    }


    public Optional<ItemDef> getItemDefByOid(@Nullable String aOid)
    {
        return getItemDefs()//
                .filter(i -> Objects.equals(aOid, i.getOid()))//
                .findAny();
    }


    public Optional<ItemDef> getItemDefByName(@Nullable String aName)
    {
        return getItemDefByName(aName, namesCaseInsensitive);
    }


    public Optional<ItemDef> getItemDefByName(@Nullable String aName,
            boolean aCompareCaseInsensitive)
    {
        return filterByName(getItemDefs(), aName, aCompareCaseInsensitive);
    }


    public Stream<CodeList> getCodeLists()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getCodeLists()))//
                .filter(Objects::nonNull);
    }


    public Stream<MethodDef> getMethodDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getMethodDefs()))//
                .filter(Objects::nonNull);
    }


    public Stream<ValueListDef> getValueListDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getValueListDefs()))//
                .filter(Objects::nonNull);
    }


    public Stream<WhereClauseDef> getWhereClauseDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getWhereClauseDefs()))//
                .filter(Objects::nonNull);
    }


    public Stream<CommentDef> getCommentDefs()
    {
        return getMetaDataVersions()//
                .flatMap(m -> list2Stream(m.getCommentDefs()))//
                .filter(Objects::nonNull);
    }


    public Stream<Standard> getStandards()
    {
        return getMetaDataVersions()//
                .flatMap(this::map2Standard)//
                .filter(Objects::nonNull);
    }


    public Optional<CodeList> getCodeListByOid(@Nullable String aOid)
    {
        return getCodeLists()//
                .filter(cdl -> Objects.equals(aOid, cdl.getOid()))//
                .findAny();
    }


    public Optional<MethodDef> getMethodDefByOID(@Nullable String aOid)
    {
        return getMethodDefs()//
                .filter(md -> Objects.equals(aOid, md.getOid()))//
                .findAny();
    }


    public Optional<CommentDef> getCommentDefByOID(@Nullable String aOid)
    {
        return getCommentDefs()//
                .filter(cd -> Objects.equals(aOid, cd.getOid()))//
                .findAny();
    }


    public Optional<Standard> getStandardByOID(@Nullable String aOid)
    {
        return getStandards()//
                .filter(std -> Objects.equals(aOid, std.getOid()))//
                .findAny();
    }


    public Optional<WhereClauseDef> getWhereClauseDefByOid(@Nullable String aOid)
    {
        return getWhereClauseDefs()//
                .filter(wc -> Objects.equals(aOid, wc.getOid()))//
                .findAny();
    }


    public Optional<ValueListDef> getValueListDefByOid(@Nullable String aOid)
    {
        return getValueListDefs()//
                .filter(vl -> Objects.equals(aOid, vl.getOid()))//
                .findAny();
    }


    public Optional<ItemGroupDef> getItemGroupDefByName(@Nullable String aName)
    {
        return getItemGroupDefByName(aName, namesCaseInsensitive);
    }


    public Optional<ItemGroupDef> getItemGroupDefByName(@Nullable String aName,
            boolean aCompareCaseInsensitive)
    {
        return filterByName(getItemGroupDefs(), aName, aCompareCaseInsensitive);
    }


    public Optional<ItemGroupDef> getItemGroupDefByOid(@Nullable String aOid)
    {
        return getItemGroupDefs()//
                .filter(v -> Objects.equals(aOid, v.getOid()))//
                .findAny();
    }


    public Optional<ItemGroupDef> getItemGroupDefByURI(@Nullable URI aUri)
    {
        return getItemGroupDefs()//
                .filter(v -> Objects.equals(aUri, getUriFor(v)))//
                .findAny();
    }


    public Stream<ItemDef> getItemsForItemGroup(@Nullable ItemGroupDef aGroup)
    {
        if (aGroup == null)
        {
            return Stream.empty();
        }
        return list2Stream(aGroup.getItemRefs())//
                .map(ItemRef::getItemOID)//
                .map(this::getItemDefByOid)//
                .filter(Optional::isPresent)//
                .map(Optional::get)//
        ;
    }


    public Optional<ItemDef> getItemForName(@Nullable ItemGroupDef aGroup, @Nullable String aName)
    {
        return getItemForName(aGroup, aName, namesCaseInsensitive);
    }


    public Optional<ItemDef> getItemForName(@Nullable ItemGroupDef aGroup, @Nullable String aName,
            boolean aCompareCaseInsensitive)
    {
        return filterByName(getItemsForItemGroup(aGroup), aName, aCompareCaseInsensitive);
    }


    public @Nullable URI getUriFor(@Nullable ItemGroupDef aItemGroup)
    {
        if (aItemGroup == null)
        {
            return null;
        }
        Leaf l = aItemGroup.getLeaf();
        if (l == null)
        {
            return null;
        }
        String href = l.getHref();
        if (href == null)
        {
            return null;
        }
        return uri.resolve(href);
    }

}
