import { useLocalSearchParams } from 'expo-router';
import { CollectionContent } from '@/components/collection-content';
import { type CollectionKind } from '@/modules/keyflow-library/src';

export default function CollectionScreen() {
  const params = useLocalSearchParams<{ kind?: string; id?: string }>();
  const kind: CollectionKind = params.kind === 'favorites' || params.kind === 'recent' ? params.kind : 'playlist';
  return <CollectionContent kind={kind} id={params.id ?? null} />;
}
