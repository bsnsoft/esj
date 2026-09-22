using System;
using System.Collections.Generic;

namespace En16931.SemanticJson.Typed;

/// <summary>
/// The paths of a document in canonical order, so that a typed view can ask whether a
/// business group instance exists without walking the whole document for every question.
/// </summary>
/// <remarks>
/// The canonical path order puts the paths of one subtree next to each other, so the question
/// <em>does anything hang under this instance</em> is one binary search. A view builds this
/// once at its root and every view below it shares it.
/// </remarks>
public sealed class PathIndex
{
    private readonly List<SemanticPath> _paths;

    /// <summary>Indexes a document.</summary>
    /// <param name="document">the document</param>
    public PathIndex(SemanticDocument document)
    {
        ArgumentNullException.ThrowIfNull(document);
        _paths = new List<SemanticPath>(document.Values.Keys);
    }

    /// <summary>Tells whether the document carries anything at or below a path.</summary>
    /// <param name="prefix">the path</param>
    /// <returns>whether the subtree holds a value</returns>
    public bool Any(SemanticPath prefix)
    {
        ArgumentNullException.ThrowIfNull(prefix);
        int at = LowerBound(prefix);
        return at < _paths.Count && _paths[at].StartsWith(prefix);
    }

    private int LowerBound(SemanticPath prefix)
    {
        int low = 0;
        int high = _paths.Count;
        while (low < high)
        {
            int middle = (low + high) / 2;
            if (SemanticPath.CanonicalOrder.Compare(_paths[middle], prefix) < 0)
            {
                low = middle + 1;
            }
            else
            {
                high = middle;
            }
        }

        return low;
    }
}

/// <summary>
/// The base of the generated read views: one business group instance of a document, and the
/// accessors a generated class is written in terms of.
/// </summary>
/// <remarks>
/// A view reads and never writes. It carries no copy of the document: every accessor answers
/// from the document it was handed, so a view is cheap to make and never goes stale. The
/// occurrences of a repeatable term or group are dense and zero-based (specification,
/// section 5.4), so a list ends at the first index the document does not carry.
/// </remarks>
public abstract class TypedView
{
    /// <summary>Creates a view of one business group instance.</summary>
    /// <param name="document">the document</param>
    /// <param name="index">the index of its paths</param>
    /// <param name="path">the instance this view is of, the root for the document itself</param>
    protected TypedView(SemanticDocument document, PathIndex index, SemanticPath path)
    {
        ArgumentNullException.ThrowIfNull(document);
        ArgumentNullException.ThrowIfNull(index);
        ArgumentNullException.ThrowIfNull(path);
        Document = document;
        Index = index;
        Instance = path;
    }

    /// <summary>Returns the document this view reads.</summary>
    public SemanticDocument Document { get; }

    /// <summary>Returns the path of the business group instance this view is of.</summary>
    public SemanticPath Instance { get; }

    /// <summary>Returns the index of the paths of the document.</summary>
    protected PathIndex Index { get; }

    /// <summary>Returns the value of a term that occurs at most once, or <c>null</c>.</summary>
    /// <param name="term">the identifier of the term</param>
    /// <returns>the value, or <c>null</c></returns>
    protected SemanticValue? Read(string term) => Document.Value(Step(term));

    /// <summary>Returns the values of a repeatable term, in the order of their indices.</summary>
    /// <param name="term">the identifier of the term</param>
    /// <returns>the values, empty where the document carries none</returns>
    protected IReadOnlyList<SemanticValue> ReadAll(string term)
    {
        List<SemanticValue> values = new();
        for (int index = 0; ; index++)
        {
            SemanticValue? value = Document.Value(Step(term, index));
            if (value is null)
            {
                return values;
            }

            values.Add(value);
        }
    }

    /// <summary>Tells whether a business group that occurs at most once has an instance.</summary>
    /// <param name="group">the identifier of the group</param>
    /// <returns>whether the document carries anything under it</returns>
    protected bool GroupPresent(string group) => Index.Any(Step(group));

    /// <summary>Returns the instances of a repeatable business group.</summary>
    /// <typeparam name="T">the generated view of that group</typeparam>
    /// <param name="group">the identifier of the group</param>
    /// <param name="make">how to make a view of one instance</param>
    /// <returns>the instances, in the order of their indices</returns>
    protected IReadOnlyList<T> GroupList<T>(
        string group, Func<SemanticDocument, PathIndex, SemanticPath, T> make)
    {
        ArgumentNullException.ThrowIfNull(make);
        List<T> instances = new();
        for (int index = 0; ; index++)
        {
            SemanticPath path = Step(group, index);
            if (!Index.Any(path))
            {
                return instances;
            }

            instances.Add(make(Document, Index, path));
        }
    }

    /// <summary>Returns the path of a child term or group of this instance.</summary>
    /// <param name="term">the identifier of the child</param>
    /// <returns>the path</returns>
    protected SemanticPath Step(string term) => SemanticPath.Parse(Instance.Text + "/" + term);

    /// <summary>Returns the path of one occurrence of a repeatable child of this instance.</summary>
    /// <param name="term">the identifier of the child</param>
    /// <param name="index">the occurrence</param>
    /// <returns>the path</returns>
    protected SemanticPath Step(string term, int index) =>
        SemanticPath.Parse(Instance.Text + "/" + term + "/"
            + index.ToString(System.Globalization.CultureInfo.InvariantCulture));
}
