using System;

namespace En16931.SemanticJson.Json;

/// <summary>
/// The resource bounds a reader enforces (specification, section 12.2).
/// </summary>
/// <remarks>
/// A limit is a policy of the reader and not a property of the document: exceeding one is
/// reported as <c>ESJ-L1-LIMIT</c> and says that this reader, as configured, declines to
/// process the document. The defaults are those of the specification, so that two
/// implementations that adopt them refuse the same documents. The 64-character bound on a
/// decimal form and the 128-character bound on an owner token are deliberately not here:
/// they belong to the grammars they are stated with and are not configurable.
/// </remarks>
public sealed class Limits
{
    private const long Mib = 1024L * 1024L;

    private Limits(
        long maxDocumentBytes,
        int maxValues,
        int maxValueMembers,
        int maxPathSegments,
        int maxPathBytes,
        long maxStringBytes,
        long maxBinaryValueBytes,
        long maxTotalBinaryBytes,
        int maxExtensionDepth,
        int maxExtensionNodes)
    {
        MaxDocumentBytes = Positive(maxDocumentBytes, nameof(maxDocumentBytes));
        MaxValues = (int)Positive(maxValues, nameof(maxValues));
        MaxValueMembers = (int)Positive(maxValueMembers, nameof(maxValueMembers));
        MaxPathSegments = (int)Positive(maxPathSegments, nameof(maxPathSegments));
        MaxPathBytes = (int)Positive(maxPathBytes, nameof(maxPathBytes));
        MaxStringBytes = Positive(maxStringBytes, nameof(maxStringBytes));
        MaxBinaryValueBytes = Positive(maxBinaryValueBytes, nameof(maxBinaryValueBytes));
        MaxTotalBinaryBytes = Positive(maxTotalBinaryBytes, nameof(maxTotalBinaryBytes));
        MaxExtensionDepth = (int)Positive(maxExtensionDepth, nameof(maxExtensionDepth));
        MaxExtensionNodes = (int)Positive(maxExtensionNodes, nameof(maxExtensionNodes));
    }

    /// <summary>Returns the limits of the specification, section 12.2.</summary>
    public static Limits Defaults { get; } = new(
        64L * Mib, 100_000, 16, 16, 256, Mib, 32L * Mib, 48L * Mib, 32, 100_000);

    /// <summary>Returns the largest document, in bytes of the encoded document.</summary>
    public long MaxDocumentBytes { get; }

    /// <summary>Returns the largest number of members of <c>values</c>.</summary>
    public int MaxValues { get; }

    /// <summary>Returns the largest number of members of one value object.</summary>
    public int MaxValueMembers { get; }

    /// <summary>Returns the largest number of segments of one semantic path.</summary>
    public int MaxPathSegments { get; }

    /// <summary>Returns the largest semantic path, in bytes of its UTF-8 encoding.</summary>
    public int MaxPathBytes { get; }

    /// <summary>Returns the largest string value, in bytes of its UTF-8 encoding.</summary>
    public long MaxStringBytes { get; }

    /// <summary>Returns the largest content of a value that carries a binary component.</summary>
    public long MaxBinaryValueBytes { get; }

    /// <summary>Returns the largest sum of decoded binary content of one document.</summary>
    public long MaxTotalBinaryBytes { get; }

    /// <summary>Returns the deepest nesting admitted inside <c>extensions</c>.</summary>
    public int MaxExtensionDepth { get; }

    /// <summary>Returns the largest number of nodes inside <c>extensions</c>.</summary>
    public int MaxExtensionNodes { get; }

    /// <summary>Returns a builder seeded with these limits.</summary>
    /// <returns>the builder</returns>
    public Builder ToBuilder() => new(this);

    private static long Positive(long value, string what) => value > 0
        ? value
        : throw new ArgumentOutOfRangeException(what, value, what + " is a positive bound");

    /// <summary>Builds a set of limits from the defaults, one bound at a time.</summary>
    public sealed class Builder
    {
        private long _maxDocumentBytes;
        private int _maxValues;
        private int _maxValueMembers;
        private int _maxPathSegments;
        private int _maxPathBytes;
        private long _maxStringBytes;
        private long _maxBinaryValueBytes;
        private long _maxTotalBinaryBytes;
        private int _maxExtensionDepth;
        private int _maxExtensionNodes;

        internal Builder(Limits seed)
        {
            _maxDocumentBytes = seed.MaxDocumentBytes;
            _maxValues = seed.MaxValues;
            _maxValueMembers = seed.MaxValueMembers;
            _maxPathSegments = seed.MaxPathSegments;
            _maxPathBytes = seed.MaxPathBytes;
            _maxStringBytes = seed.MaxStringBytes;
            _maxBinaryValueBytes = seed.MaxBinaryValueBytes;
            _maxTotalBinaryBytes = seed.MaxTotalBinaryBytes;
            _maxExtensionDepth = seed.MaxExtensionDepth;
            _maxExtensionNodes = seed.MaxExtensionNodes;
        }

        /// <summary>Sets the largest document, in bytes.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxDocumentBytes(long value)
        {
            _maxDocumentBytes = value;
            return this;
        }

        /// <summary>Sets the largest number of members of <c>values</c>.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxValues(int value)
        {
            _maxValues = value;
            return this;
        }

        /// <summary>Sets the largest number of members of one value object.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxValueMembers(int value)
        {
            _maxValueMembers = value;
            return this;
        }

        /// <summary>Sets the largest number of segments of one semantic path.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxPathSegments(int value)
        {
            _maxPathSegments = value;
            return this;
        }

        /// <summary>Sets the largest semantic path, in bytes.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxPathBytes(int value)
        {
            _maxPathBytes = value;
            return this;
        }

        /// <summary>Sets the largest string value, in bytes.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxStringBytes(long value)
        {
            _maxStringBytes = value;
            return this;
        }

        /// <summary>Sets the largest content of a value carrying a binary component.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxBinaryValueBytes(long value)
        {
            _maxBinaryValueBytes = value;
            return this;
        }

        /// <summary>Sets the largest sum of decoded binary content of one document.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxTotalBinaryBytes(long value)
        {
            _maxTotalBinaryBytes = value;
            return this;
        }

        /// <summary>Sets the deepest nesting admitted inside <c>extensions</c>.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxExtensionDepth(int value)
        {
            _maxExtensionDepth = value;
            return this;
        }

        /// <summary>Sets the largest number of nodes inside <c>extensions</c>.</summary>
        /// <param name="value">the bound</param>
        /// <returns>this builder</returns>
        public Builder MaxExtensionNodes(int value)
        {
            _maxExtensionNodes = value;
            return this;
        }

        /// <summary>Builds the limits.</summary>
        /// <returns>the limits</returns>
        public Limits Build() => new(
            _maxDocumentBytes, _maxValues, _maxValueMembers, _maxPathSegments, _maxPathBytes,
            _maxStringBytes, _maxBinaryValueBytes, _maxTotalBinaryBytes, _maxExtensionDepth,
            _maxExtensionNodes);
    }
}
